#include <QJsonDocument>
#include <QJsonObject>
#include <QRegularExpression>
#include <QTcpServer>
#include <QTcpSocket>
#include <QTemporaryDir>
#include <QTest>

#include <functional>

#include <servers/AddressService.h>
#include <servers/ServerManager.h>

// Stands in for the address service (services/dns-worker): answers POSTs with JSON, as each test decides.
class FakeAddressApi : public QObject {
    Q_OBJECT
   public:
    struct Call {
        QString path;
        QJsonObject body;
    };
    using Handler = std::function<QPair<int, QJsonObject>(const Call&)>;

    explicit FakeAddressApi(Handler handler) : m_handler(std::move(handler))
    {
        m_server.listen(QHostAddress::LocalHost);
        connect(&m_server, &QTcpServer::newConnection, this, [this] {
            while (auto* socket = m_server.nextPendingConnection()) {
                connect(socket, &QTcpSocket::readyRead, this, [this, socket] { read(socket); });
                connect(socket, &QTcpSocket::disconnected, socket, &QObject::deleteLater);
            }
        });
    }

    QUrl url() const { return QUrl(QStringLiteral("http://127.0.0.1:%1").arg(m_server.serverPort())); }
    QList<Call> calls;

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
        const int bodyLength = length.match(headers).captured(1).toInt();
        if (buffer.size() < headerEnd + 4 + bodyLength) {
            return;
        }
        const Call call{ headers.section(' ', 1, 1), QJsonDocument::fromJson(buffer.mid(headerEnd + 4, bodyLength)).object() };
        m_buffers.remove(socket);
        calls << call;
        const auto [status, json] = m_handler(call);
        const QByteArray body = QJsonDocument(json).toJson(QJsonDocument::Compact);
        socket->write(QStringLiteral("HTTP/1.1 %1 X\r\nContent-Type: application/json\r\nContent-Length: %2\r\nConnection: close\r\n\r\n")
                          .arg(status)
                          .arg(body.size())
                          .toUtf8() +
                      body);
        socket->disconnectFromHost();
    }

    QTcpServer m_server;
    Handler m_handler;
    QHash<QTcpSocket*, QByteArray> m_buffers;
};

class AddressServiceTest : public QObject {
    Q_OBJECT

    static ServerConfig config(const QString& address, const QString& secret = QString(), int port = 25565)
    {
        ServerConfig c;
        c.name = QStringLiteral("Test");
        c.address = address;
        c.addressSecret = secret;
        c.port = port;
        return c;
    }

    // a well-behaved service: claims succeed, updates answer with the address
    static QPair<int, QJsonObject> happy(const FakeAddressApi::Call& call)
    {
        const QString name = call.body["name"].toString();
        if (call.path == "/v1/claim") {
            return { 201, { { "name", name }, { "address", name + ".yahuclient.net" }, { "secret", "s3cret" } } };
        }
        return { 200, { { "address", name + ".yahuclient.net" }, { "ip", "77.58.1.216" }, { "port", call.body["port"] } } };
    }

   private slots:
    void validNames_data()
    {
        QTest::addColumn<QString>("name");
        QTest::addColumn<bool>("valid");

        QTest::newRow("simple") << "chris" << true;
        QTest::newRow("dashes and digits") << "freunde-survival-2" << true;
        QTest::newRow("shortest") << "abc" << true;
        QTest::newRow("longest") << QString(32, 'x') << true;
        QTest::newRow("too short") << "ab" << false;
        QTest::newRow("too long") << QString(33, 'x') << false;
        QTest::newRow("capitals") << "Chris" << false;
        QTest::newRow("leading dash") << "-chris" << false;
        QTest::newRow("trailing dash") << "chris-" << false;
        QTest::newRow("double dash") << "chr--is" << false;
        QTest::newRow("dot") << "chr.is" << false;
        QTest::newRow("umlaut") << "grüezi" << false;
        QTest::newRow("reserved www") << "www" << false;
        QTest::newRow("reserved api") << "api" << false;
        QTest::newRow("reserved relay") << "relay" << false;
        QTest::newRow("empty") << "" << false;
    }

    void validNames()
    {
        QFETCH(QString, name);
        QFETCH(bool, valid);
        QCOMPARE(AddressService::isValidName(name), valid);
    }

    void suggestsNames_data()
    {
        QTest::addColumn<QString>("text");
        QTest::addColumn<QString>("expected");

        QTest::newRow("player") << "PinguShrek" << "pingushrek";
        QTest::newRow("underscore") << "Pingu_Shrek" << "pingu-shrek";
        QTest::newRow("server name") << "Freunde Survival!" << "freunde-survival";
        QTest::newRow("too short") << "Al" << "";
        QTest::newRow("reserved") << "API" << "";
        QTest::newRow("only symbols") << "___" << "";
        QTest::newRow("cut to 32") << QString(40, 'a') << QString(32, 'a');
    }

    void suggestsNames()
    {
        QFETCH(QString, text);
        QFETCH(QString, expected);
        QCOMPARE(AddressService::suggestName(text), expected);
    }

    void claimsThenPoints()
    {
        FakeAddressApi api(&happy);
        QTemporaryDir dir;
        YahuServer server(dir.path(), config("chris", QString(), 25570));
        AddressService service(api.url());

        service.publish(&server, "77.58.1.216");
        QCOMPARE(service.status(&server).state, AddressService::State::Working);
        QTRY_COMPARE(service.status(&server).state, AddressService::State::Ready);
        QCOMPARE(service.status(&server).address, QStringLiteral("chris.yahuclient.net"));

        QCOMPARE(api.calls.size(), 2);
        QCOMPARE(api.calls[0].path, QStringLiteral("/v1/claim"));
        QCOMPARE(api.calls[1].path, QStringLiteral("/v1/update"));
        QCOMPARE(api.calls[1].body["secret"].toString(), QStringLiteral("s3cret"));
        QCOMPARE(api.calls[1].body["port"].toInt(), 25570);
        QCOMPARE(api.calls[1].body["ip"].toString(), QStringLiteral("77.58.1.216"));
        // the secret is kept with the server, so the next start only updates
        QCOMPARE(server.config().addressSecret, QStringLiteral("s3cret"));
        QCOMPARE(ServerConfig::fromJson(server.config().toJson()).addressSecret, QStringLiteral("s3cret"));
    }

    void knownNameOnlyUpdates()
    {
        FakeAddressApi api(&happy);
        QTemporaryDir dir;
        YahuServer server(dir.path(), config("chris", "s3cret"));
        AddressService service(api.url());

        service.publish(&server, QString());
        QTRY_COMPARE(service.status(&server).state, AddressService::State::Ready);
        QCOMPARE(api.calls.size(), 1);
        QCOMPARE(api.calls[0].path, QStringLiteral("/v1/update"));
        QVERIFY(!api.calls[0].body.contains("ip"));  // unknown: the service takes the caller's address
    }

    void takenName()
    {
        FakeAddressApi api([](const FakeAddressApi::Call&) { return QPair<int, QJsonObject>{ 409, { { "error", "taken" } } }; });
        QTemporaryDir dir;
        YahuServer server(dir.path(), config("chris"));
        AddressService service(api.url());

        service.publish(&server, "77.58.1.216");
        QTRY_COMPARE(service.status(&server).state, AddressService::State::Taken);
        QVERIFY(service.status(&server).detail.contains("chris.yahuclient.net"));
        QVERIFY(server.config().addressSecret.isEmpty());
    }

    void lostSecretClaimsAgain()
    {
        // the stored secret no longer fits (e.g. the name was released): forget it and claim fresh
        int updates = 0;
        FakeAddressApi api([&updates](const FakeAddressApi::Call& call) -> QPair<int, QJsonObject> {
            if (call.path == "/v1/update" && updates++ == 0) {
                return { 403, { { "error", "forbidden" } } };
            }
            return happy(call);
        });
        QTemporaryDir dir;
        YahuServer server(dir.path(), config("chris", "old"));
        AddressService service(api.url());

        service.publish(&server, "77.58.1.216");
        QTRY_COMPARE(service.status(&server).state, AddressService::State::Ready);
        QCOMPARE(api.calls.size(), 3);
        QCOMPARE(api.calls[1].path, QStringLiteral("/v1/claim"));
        QCOMPARE(server.config().addressSecret, QStringLiteral("s3cret"));
    }

    void noPublicAddress()
    {
        FakeAddressApi api([](const FakeAddressApi::Call& call) -> QPair<int, QJsonObject> {
            if (call.path == "/v1/update") {
                return { 400, { { "error", "invalid_ip" } } };
            }
            return happy(call);
        });
        QTemporaryDir dir;
        YahuServer server(dir.path(), config("chris", "s3cret"));
        AddressService service(api.url());

        service.publish(&server, QString());
        QTRY_COMPARE(service.status(&server).state, AddressService::State::Failed);
        QVERIFY(!service.status(&server).detail.isEmpty());
    }

    void serviceOffline()
    {
        QTcpServer probe;
        probe.listen(QHostAddress::LocalHost);
        const QUrl dead(QStringLiteral("http://127.0.0.1:%1").arg(probe.serverPort()));
        probe.close();

        QTemporaryDir dir;
        YahuServer server(dir.path(), config("chris", "s3cret"));
        AddressService service(dead);
        service.publish(&server, "77.58.1.216");
        QTRY_COMPARE(service.status(&server).state, AddressService::State::Failed);
    }

    void noNameNoCalls()
    {
        FakeAddressApi api(&happy);
        QTemporaryDir dir;
        YahuServer server(dir.path(), config(QString()));
        AddressService service(api.url());
        service.publish(&server, "77.58.1.216");
        QCOMPARE(service.status(&server).state, AddressService::State::None);
        QTest::qWait(100);
        QVERIFY(api.calls.isEmpty());
    }

    void invalidStoredName()
    {
        FakeAddressApi api(&happy);
        QTemporaryDir dir;
        YahuServer server(dir.path(), config("WWW"));
        AddressService service(api.url());
        service.publish(&server, "77.58.1.216");
        QCOMPARE(service.status(&server).state, AddressService::State::Failed);
        QTest::qWait(100);
        QVERIFY(api.calls.isEmpty());
    }

    void releaseSendsSecret()
    {
        FakeAddressApi api([](const FakeAddressApi::Call&) { return QPair<int, QJsonObject>{ 200, { { "released", "chris" } } }; });
        AddressService service(api.url());
        service.release("chris", "s3cret");
        service.release("chris", QString());  // nothing to prove ownership with: skipped
        QTRY_COMPARE(api.calls.size(), 1);
        QCOMPARE(api.calls[0].path, QStringLiteral("/v1/release"));
        QCOMPARE(api.calls[0].body["secret"].toString(), QStringLiteral("s3cret"));
    }
};

QTEST_GUILESS_MAIN(AddressServiceTest)

#include "AddressService_test.moc"
