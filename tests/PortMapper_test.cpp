#include <QRegularExpression>
#include <QSignalSpy>
#include <QTcpServer>
#include <QTcpSocket>
#include <QTest>

#include <functional>

#include <servers/PortMapper.h>

// A tiny HTTP server that answers UPnP SOAP calls the way a home router would. Each test decides the answers.
class FakeRouter : public QObject {
    Q_OBJECT
   public:
    struct Call {
        QString action;
        QString body;
    };
    using Handler = std::function<QPair<int, QByteArray>(const Call&)>;

    explicit FakeRouter(Handler handler) : m_handler(std::move(handler))
    {
        m_server.listen(QHostAddress::LocalHost);
        connect(&m_server, &QTcpServer::newConnection, this, [this] {
            while (auto* socket = m_server.nextPendingConnection()) {
                connect(socket, &QTcpSocket::readyRead, this, [this, socket] { read(socket); });
                connect(socket, &QTcpSocket::disconnected, socket, &QObject::deleteLater);
            }
        });
    }

    QUrl controlUrl() const { return QUrl(QStringLiteral("http://127.0.0.1:%1/ctl/IPConn").arg(m_server.serverPort())); }
    QList<Call> calls;

    QList<Call> callsOf(const QString& action) const
    {
        QList<Call> out;
        for (const auto& c : calls) {
            if (c.action == action) {
                out << c;
            }
        }
        return out;
    }

    static QByteArray envelope(const QString& inner)
    {
        return QStringLiteral(
                   "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>%1</s:Body></s:Envelope>")
            .arg(inner)
            .toUtf8();
    }
    static QPair<int, QByteArray> ok(const QString& inner = QString()) { return { 200, envelope(inner) }; }
    static QPair<int, QByteArray> fault(int code, const QString& text)
    {
        return { 500, envelope(QStringLiteral("<s:Fault><detail><UPnPError><errorCode>%1</errorCode><errorDescription>%2</errorDescription>"
                                              "</UPnPError></detail></s:Fault>")
                                   .arg(code)
                                   .arg(text)) };
    }
    static QPair<int, QByteArray> externalIp(const QString& ip)
    {
        return ok(QStringLiteral("<u:GetExternalIPAddressResponse><NewExternalIPAddress>%1</NewExternalIPAddress>"
                                 "</u:GetExternalIPAddressResponse>")
                      .arg(ip));
    }

   private:
    void read(QTcpSocket* socket)
    {
        QByteArray& buffer = m_buffers[socket];
        buffer += socket->readAll();
        const int headerEnd = buffer.indexOf("\r\n\r\n");
        if (headerEnd < 0) {
            return;
        }
        const QString headers = QString::fromUtf8(buffer.left(headerEnd));
        static const QRegularExpression length(QStringLiteral("content-length:\\s*(\\d+)"), QRegularExpression::CaseInsensitiveOption);
        static const QRegularExpression action(QStringLiteral("soapaction:\\s*\"[^#]*#([^\"]+)\""), QRegularExpression::CaseInsensitiveOption);
        const int bodyLength = length.match(headers).captured(1).toInt();
        if (buffer.size() < headerEnd + 4 + bodyLength) {
            return;
        }
        const Call call{ action.match(headers).captured(1), QString::fromUtf8(buffer.mid(headerEnd + 4, bodyLength)) };
        m_buffers.remove(socket);
        calls << call;
        const auto [status, body] = m_handler(call);
        socket->write(QStringLiteral("HTTP/1.1 %1 %2\r\nContent-Type: text/xml\r\nContent-Length: %3\r\nConnection: close\r\n\r\n")
                          .arg(status)
                          .arg(status == 200 ? QStringLiteral("OK") : QStringLiteral("Internal Server Error"))
                          .arg(body.size())
                          .toUtf8() +
                      body);
        socket->disconnectFromHost();
    }

    QTcpServer m_server;
    Handler m_handler;
    QHash<QTcpSocket*, QByteArray> m_buffers;
};

class PortMapperTest : public QObject {
    Q_OBJECT

    static constexpr int kPort = 25565;
    const QString kService = QStringLiteral("urn:schemas-upnp-org:service:WANIPConnection:1");
    const QHostAddress kLocal = QHostAddress(QStringLiteral("192.168.1.50"));

   private slots:
    void sharedAddresses_data()
    {
        QTest::addColumn<QString>("ip");
        QTest::addColumn<bool>("shared");

        QTest::newRow("public") << "203.0.113.7" << false;
        QTest::newRow("another public") << "77.58.1.216" << false;
        QTest::newRow("home network") << "192.168.1.1" << true;
        QTest::newRow("10/8") << "10.0.0.138" << true;
        QTest::newRow("172.16/12") << "172.20.4.1" << true;
        QTest::newRow("carrier-grade NAT") << "100.64.12.3" << true;
        QTest::newRow("CGNAT upper end") << "100.127.255.254" << true;
        QTest::newRow("just above CGNAT") << "100.128.0.1" << false;
        QTest::newRow("empty") << "" << true;
    }

    void sharedAddresses()
    {
        QFETCH(QString, ip);
        QFETCH(bool, shared);
        QCOMPARE(PortMapper::isSharedAddress(QHostAddress(ip)), shared);
    }

    void parsesRouterDescription()
    {
        // a nested IGD like the Sagemcom ones: the WAN service sits two devices deep, with a relative control URL
        const QByteArray xml = R"(<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
  <device>
    <deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:1</deviceType>
    <serviceList><service><serviceType>urn:schemas-upnp-org:service:Layer3Forwarding:1</serviceType>
      <controlURL>/upnp/control/Layer3Forwarding</controlURL></service></serviceList>
    <deviceList><device><deviceType>urn:schemas-upnp-org:device:WANDevice:1</deviceType>
      <deviceList><device><deviceType>urn:schemas-upnp-org:device:WANConnectionDevice:1</deviceType>
        <serviceList><service>
          <serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>
          <controlURL>/upnp/control/WANIPConnection0</controlURL>
        </service></serviceList>
      </device></deviceList>
    </device></deviceList>
  </device>
</root>)";
        QUrl control;
        QString type;
        QVERIFY(PortMapper::parseDescription(xml, QUrl("http://192.168.1.1:49153/IGDdevicedesc_brlan0.xml"), &control, &type));
        QCOMPARE(control, QUrl("http://192.168.1.1:49153/upnp/control/WANIPConnection0"));
        QCOMPARE(type, QStringLiteral("urn:schemas-upnp-org:service:WANIPConnection:1"));
    }

    void parsesUrlBaseAndPpp()
    {
        const QByteArray xml = R"(<root><URLBase>http://10.0.0.1:5000/</URLBase><device><serviceList><service>
            <serviceType>urn:schemas-upnp-org:service:WANPPPConnection:1</serviceType>
            <controlURL>ctl/PPPConn</controlURL></service></serviceList></device></root>)";
        QUrl control;
        QString type;
        QVERIFY(PortMapper::parseDescription(xml, QUrl("http://10.0.0.1:1900/desc.xml"), &control, &type));
        QCOMPARE(control, QUrl("http://10.0.0.1:5000/ctl/PPPConn"));
        QVERIFY(type.contains("WANPPPConnection"));
    }

    void rejectsDevicesWithoutWanService()
    {
        // a smart TV or a printer also answers the search, but has nothing to forward ports with
        const QByteArray xml = R"(<root><device><serviceList><service>
            <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
            <controlURL>/rc</controlURL></service></serviceList></device></root>)";
        QUrl control;
        QString type;
        QVERIFY(!PortMapper::parseDescription(xml, QUrl("http://192.168.1.20/desc.xml"), &control, &type));
        QVERIFY(!PortMapper::parseDescription("not xml at all", QUrl("http://192.168.1.20/"), &control, &type));
    }

    void readsFaults()
    {
        QCOMPARE(PortMapper::faultFromBody(FakeRouter::fault(718, "ConflictInMappingEntry").second), QStringLiteral("718 ConflictInMappingEntry"));
        QCOMPARE(PortMapper::faultFromBody(FakeRouter::fault(725, "").second), QStringLiteral("725"));
        QVERIFY(PortMapper::faultFromBody(FakeRouter::ok().second).isEmpty());
    }

    void opensPortAndReportsAddress()
    {
        FakeRouter router([](const FakeRouter::Call& call) {
            return call.action == "GetExternalIPAddress" ? FakeRouter::externalIp("203.0.113.7") : FakeRouter::ok();
        });
        PortMapper mapper;
        mapper.useGateway(router.controlUrl(), kService, kLocal);
        mapper.open(kPort, "Yahu Client: Test");
        QCOMPARE(mapper.mapping(kPort).state, PortMapper::State::Opening);

        QTRY_COMPARE(mapper.mapping(kPort).state, PortMapper::State::Open);
        QCOMPARE(mapper.mapping(kPort).externalAddress, QStringLiteral("203.0.113.7"));

        const auto adds = router.callsOf("AddPortMapping");
        QCOMPARE(adds.size(), 1);
        QVERIFY(adds[0].body.contains("<NewExternalPort>25565</NewExternalPort>"));
        QVERIFY(adds[0].body.contains("<NewInternalPort>25565</NewInternalPort>"));
        QVERIFY(adds[0].body.contains("<NewProtocol>TCP</NewProtocol>"));
        QVERIFY(adds[0].body.contains("<NewInternalClient>192.168.1.50</NewInternalClient>"));
        QVERIFY(adds[0].body.contains("<NewPortMappingDescription>Yahu Client: Test</NewPortMappingDescription>"));
        QVERIFY(adds[0].body.contains("<NewLeaseDuration>3600</NewLeaseDuration>"));
    }

    void escapesDescription()
    {
        FakeRouter router([](const FakeRouter::Call& call) {
            return call.action == "GetExternalIPAddress" ? FakeRouter::externalIp("203.0.113.7") : FakeRouter::ok();
        });
        PortMapper mapper;
        mapper.useGateway(router.controlUrl(), kService, kLocal);
        mapper.open(kPort, "Yahu Client: Tom & Jerry <3");
        QTRY_COMPARE(mapper.mapping(kPort).state, PortMapper::State::Open);
        QVERIFY(router.callsOf("AddPortMapping")[0].body.contains("Tom &amp; Jerry &lt;3"));
    }

    void retriesWithoutLeaseForOldRouters()
    {
        int adds = 0;
        FakeRouter router([&adds](const FakeRouter::Call& call) {
            if (call.action == "AddPortMapping" && adds++ == 0) {
                return FakeRouter::fault(725, "OnlyPermanentLeasesSupported");
            }
            return call.action == "GetExternalIPAddress" ? FakeRouter::externalIp("203.0.113.7") : FakeRouter::ok();
        });
        PortMapper mapper;
        mapper.useGateway(router.controlUrl(), kService, kLocal);
        mapper.open(kPort, "Yahu Client: Test");

        QTRY_COMPARE(mapper.mapping(kPort).state, PortMapper::State::Open);
        const auto calls = router.callsOf("AddPortMapping");
        QCOMPARE(calls.size(), 2);
        QVERIFY(calls[0].body.contains("<NewLeaseDuration>3600</NewLeaseDuration>"));
        QVERIFY(calls[1].body.contains("<NewLeaseDuration>0</NewLeaseDuration>"));
    }

    void reportsPortTakenByAnotherDevice()
    {
        FakeRouter router([](const FakeRouter::Call&) { return FakeRouter::fault(718, "ConflictInMappingEntry"); });
        PortMapper mapper;
        mapper.useGateway(router.controlUrl(), kService, kLocal);
        mapper.open(kPort, "Yahu Client: Test");

        QTRY_COMPARE(mapper.mapping(kPort).state, PortMapper::State::Refused);
        QVERIFY(mapper.mapping(kPort).detail.contains("25565"));
    }

    void reportsOtherRefusals()
    {
        FakeRouter router([](const FakeRouter::Call&) { return FakeRouter::fault(606, "Action not authorized"); });
        PortMapper mapper;
        mapper.useGateway(router.controlUrl(), kService, kLocal);
        mapper.open(kPort, "Yahu Client: Test");

        QTRY_COMPARE(mapper.mapping(kPort).state, PortMapper::State::Refused);
        QVERIFY(mapper.mapping(kPort).detail.contains("606 Action not authorized"));
    }

    void detectsSharedAddress()
    {
        FakeRouter router([](const FakeRouter::Call& call) {
            return call.action == "GetExternalIPAddress" ? FakeRouter::externalIp("100.64.7.9") : FakeRouter::ok();
        });
        PortMapper mapper;
        mapper.useGateway(router.controlUrl(), kService, kLocal);
        mapper.open(kPort, "Yahu Client: Test");

        QTRY_COMPARE(mapper.mapping(kPort).state, PortMapper::State::SharedAddress);
        QVERIFY(!mapper.mapping(kPort).detail.isEmpty());
    }

    void staysOpenWhenRouterHidesAddress()
    {
        // some routers open the port fine but refuse to say their address; the page then looks it up itself
        FakeRouter router([](const FakeRouter::Call& call) {
            return call.action == "GetExternalIPAddress" ? FakeRouter::fault(501, "ActionFailed") : FakeRouter::ok();
        });
        PortMapper mapper;
        mapper.useGateway(router.controlUrl(), kService, kLocal);
        mapper.open(kPort, "Yahu Client: Test");

        QTRY_COMPARE(mapper.mapping(kPort).state, PortMapper::State::Open);
        QVERIFY(mapper.mapping(kPort).externalAddress.isEmpty());
    }

    void closeRemovesMapping()
    {
        FakeRouter router([](const FakeRouter::Call& call) {
            return call.action == "GetExternalIPAddress" ? FakeRouter::externalIp("203.0.113.7") : FakeRouter::ok();
        });
        PortMapper mapper;
        mapper.useGateway(router.controlUrl(), kService, kLocal);
        mapper.open(kPort, "Yahu Client: Test");
        QTRY_COMPARE(mapper.mapping(kPort).state, PortMapper::State::Open);

        QSignalSpy changed(&mapper, &PortMapper::mappingChanged);
        mapper.close(kPort);
        QCOMPARE(mapper.mapping(kPort).state, PortMapper::State::Closed);
        QCOMPARE(changed.size(), 1);
        QTRY_COMPARE(router.callsOf("DeletePortMapping").size(), 1);
        QVERIFY(router.callsOf("DeletePortMapping")[0].body.contains("<NewExternalPort>25565</NewExternalPort>"));
    }

    void stoppingWhileOpeningLeavesNothingBehind()
    {
        // the server is stopped before the router has answered: the late mapping must be removed again
        FakeRouter router([](const FakeRouter::Call& call) {
            return call.action == "GetExternalIPAddress" ? FakeRouter::externalIp("203.0.113.7") : FakeRouter::ok();
        });
        PortMapper mapper;
        mapper.useGateway(router.controlUrl(), kService, kLocal);
        mapper.open(kPort, "Yahu Client: Test");
        mapper.close(kPort);

        QTRY_COMPARE(router.callsOf("DeletePortMapping").size(), 1);
        QCOMPARE(mapper.mapping(kPort).state, PortMapper::State::Closed);
    }

    void closeAllWaitsForRouter()
    {
        FakeRouter router([](const FakeRouter::Call& call) {
            return call.action == "GetExternalIPAddress" ? FakeRouter::externalIp("203.0.113.7") : FakeRouter::ok();
        });
        PortMapper mapper;
        mapper.useGateway(router.controlUrl(), kService, kLocal);
        mapper.open(25565, "a");
        mapper.open(25566, "b");
        QTRY_COMPARE(mapper.mapping(25565).state, PortMapper::State::Open);
        QTRY_COMPARE(mapper.mapping(25566).state, PortMapper::State::Open);

        mapper.closeAll(3000);
        QCOMPARE(router.callsOf("DeletePortMapping").size(), 2);
        QCOMPARE(mapper.mapping(25565).state, PortMapper::State::Closed);
    }

    void reportsRouterThatStoppedAnswering()
    {
        // nothing listens on this port, so the connection is refused
        QTcpServer probe;
        probe.listen(QHostAddress::LocalHost);
        const QUrl dead(QStringLiteral("http://127.0.0.1:%1/ctl").arg(probe.serverPort()));
        probe.close();

        PortMapper mapper;
        mapper.useGateway(dead, kService, kLocal);
        mapper.open(kPort, "Yahu Client: Test");
        QTRY_COMPARE(mapper.mapping(kPort).state, PortMapper::State::NoRouter);
    }

    void openTwiceAsksOnce()
    {
        FakeRouter router([](const FakeRouter::Call& call) {
            return call.action == "GetExternalIPAddress" ? FakeRouter::externalIp("203.0.113.7") : FakeRouter::ok();
        });
        PortMapper mapper;
        mapper.useGateway(router.controlUrl(), kService, kLocal);
        mapper.open(kPort, "Yahu Client: Test");
        mapper.open(kPort, "Yahu Client: Test");
        QTRY_COMPARE(mapper.mapping(kPort).state, PortMapper::State::Open);
        QCOMPARE(router.callsOf("AddPortMapping").size(), 1);
    }
};

QTEST_GUILESS_MAIN(PortMapperTest)

#include "PortMapper_test.moc"
