// SPDX-License-Identifier: GPL-3.0-only
/*
 *  Yahu Client
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, version 3.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "PortMapper.h"

#include <QEventLoop>
#include <QNetworkAccessManager>
#include <QNetworkInterface>
#include <QNetworkProxy>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QRegularExpression>
#include <QUdpSocket>
#include <QXmlStreamReader>

namespace {
const QHostAddress kSsdpGroup(QStringLiteral("239.255.255.250"));
constexpr quint16 kSsdpPort = 1900;
constexpr int kSearchMs = 2500;
constexpr int kLeaseSeconds = 3600;
constexpr int kRenewMs = 50 * 60 * 1000;  // renew well before the lease runs out
constexpr int kSoapTimeoutMs = 6000;

// UPnP error codes from the WANIPConnection spec
constexpr int kConflictInMappingEntry = 718;
constexpr int kOnlyPermanentLeasesSupported = 725;
}  // namespace

bool PortMapper::isSharedAddress(const QHostAddress& ip)
{
    // private ranges, carrier-grade NAT (100.64.0.0/10) and nonsense: nothing from the internet reaches us through these
    return ip.isPrivateUse() || ip.isInSubnet(QHostAddress(QStringLiteral("100.64.0.0")), 10) || ip.isLoopback() || ip.isNull();
}

bool PortMapper::parseDescription(const QByteArray& xml, const QUrl& location, QUrl* controlUrl, QString* serviceType)
{
    // walk the device description and take the first WAN connection service
    QXmlStreamReader reader(xml);
    QUrl base = location;
    QString type, control;
    while (!reader.atEnd()) {
        reader.readNext();
        if (reader.isEndElement() && reader.name() == QLatin1String("service")) {
            if (type.contains(QLatin1String("WANIPConnection")) || type.contains(QLatin1String("WANPPPConnection"))) {
                *controlUrl = base.resolved(QUrl(control));
                *serviceType = type;
                return true;
            }
            type.clear();
            control.clear();
        } else if (reader.isStartElement()) {
            if (reader.name() == QLatin1String("URLBase")) {
                const QUrl urlBase(reader.readElementText().trimmed());
                if (urlBase.isValid() && !urlBase.isEmpty()) {
                    base = urlBase;
                }
            } else if (reader.name() == QLatin1String("serviceType")) {
                type = reader.readElementText().trimmed();
            } else if (reader.name() == QLatin1String("controlURL")) {
                control = reader.readElementText().trimmed();
            }
        }
    }
    return false;
}

QString PortMapper::faultFromBody(const QByteArray& body)
{
    // UPnP faults come as HTTP 500 with <errorCode> and <errorDescription> in the body
    static const QRegularExpression code(QStringLiteral("<errorCode>\\s*(\\d+)\\s*</errorCode>"));
    static const QRegularExpression text(QStringLiteral("<errorDescription>([^<]*)</errorDescription>"));
    const QString xml = QString::fromUtf8(body);
    const auto c = code.match(xml);
    if (!c.hasMatch()) {
        return {};
    }
    const QString description = text.match(xml).captured(1).trimmed();
    return description.isEmpty() ? c.captured(1) : QStringLiteral("%1 %2").arg(c.captured(1), description);
}

void PortMapper::useGateway(const QUrl& controlUrl, const QString& serviceType, const QHostAddress& localAddress)
{
    m_gateway = { controlUrl, serviceType, localAddress };
}

PortMapper::PortMapper(QObject* parent) : QObject(parent), m_network(new QNetworkAccessManager(this))
{
    // the router is in the local network; a proxy configured for the launcher must not get in the way
    m_network->setProxy(QNetworkProxy::NoProxy);
    m_searchTimer.setSingleShot(true);
    connect(&m_searchTimer, &QTimer::timeout, this, &PortMapper::searchFinished);
    m_renewTimer.setInterval(kRenewMs);
    connect(&m_renewTimer, &QTimer::timeout, this, [this] {
        for (auto it = m_mappings.cbegin(); it != m_mappings.cend(); ++it) {
            if (it->state == State::Open) {
                addMapping(it.key(), kLeaseSeconds);
            }
        }
    });
}

PortMapper::~PortMapper()
{
    closeAll(1500);
}

void PortMapper::open(int port, const QString& description)
{
    if (m_mappings.value(port).state == State::Opening || m_mappings.value(port).state == State::Open) {
        return;
    }
    m_descriptions[port] = description;
    setMapping(port, State::Opening);
    discover([this, port] {
        if (m_mappings.value(port).state != State::Opening) {
            return;  // closed again while we were looking for the router
        }
        if (!m_gateway.controlUrl.isValid()) {
            setMapping(port, State::NoRouter, QString(),
                       tr("No router with UPnP was found. It may be switched off in the router's settings."));
            return;
        }
        addMapping(port, kLeaseSeconds);
    });
}

void PortMapper::close(int port)
{
    const State was = m_mappings.value(port).state;
    m_mappings.remove(port);
    m_descriptions.remove(port);
    if (was == State::Open && m_gateway.controlUrl.isValid()) {
        auto* reply = soap(QStringLiteral("DeletePortMapping"), { { QStringLiteral("NewRemoteHost"), QString() },
                                                                  { QStringLiteral("NewExternalPort"), QString::number(port) },
                                                                  { QStringLiteral("NewProtocol"), QStringLiteral("TCP") } });
        connect(reply, &QNetworkReply::finished, reply, &QObject::deleteLater);
    }
    bool anyOpen = false;
    for (const auto& m : std::as_const(m_mappings)) {
        anyOpen = anyOpen || m.state == State::Open;
    }
    if (!anyOpen) {
        m_renewTimer.stop();
    }
    if (was != State::Closed) {
        emit mappingChanged(port);
    }
}

void PortMapper::closeAll(int waitMs)
{
    if (!m_gateway.controlUrl.isValid()) {
        m_mappings.clear();
        return;
    }
    QList<QNetworkReply*> pending;
    for (auto it = m_mappings.cbegin(); it != m_mappings.cend(); ++it) {
        if (it->state == State::Open) {
            pending << soap(QStringLiteral("DeletePortMapping"), { { QStringLiteral("NewRemoteHost"), QString() },
                                                                   { QStringLiteral("NewExternalPort"), QString::number(it.key()) },
                                                                   { QStringLiteral("NewProtocol"), QStringLiteral("TCP") } });
        }
    }
    m_mappings.clear();
    m_renewTimer.stop();
    if (pending.isEmpty()) {
        return;
    }
    // the launcher is quitting, so wait here briefly; leftovers expire with their lease anyway
    QEventLoop loop;
    QTimer::singleShot(waitMs, &loop, &QEventLoop::quit);
    int left = pending.size();
    for (auto* reply : pending) {
        connect(reply, &QNetworkReply::finished, &loop, [&left, &loop] {
            if (--left == 0) {
                loop.quit();
            }
        });
    }
    loop.exec();
    qDeleteAll(pending);
}

void PortMapper::discover(std::function<void()> then)
{
    if (m_gateway.controlUrl.isValid()) {
        then();
        return;
    }
    m_waiting << then;
    if (m_searching) {
        return;
    }
    m_searching = true;
    m_locations.clear();

    m_socket = new QUdpSocket(this);
    if (!m_socket->bind(QHostAddress(QHostAddress::AnyIPv4), 0)) {
        searchFinished();
        return;
    }
    m_socket->setSocketOption(QAbstractSocket::MulticastTtlOption, 2);
    connect(m_socket, &QUdpSocket::readyRead, this, [this] {
        static const QRegularExpression location(QStringLiteral(R"(^location:\s*(\S+)\s*$)"),
                                                 QRegularExpression::CaseInsensitiveOption | QRegularExpression::MultilineOption);
        while (m_socket->hasPendingDatagrams()) {
            QByteArray datagram(int(m_socket->pendingDatagramSize()), Qt::Uninitialized);
            m_socket->readDatagram(datagram.data(), datagram.size());
            const auto match = location.match(QString::fromUtf8(datagram).remove('\r'));
            const QUrl url(match.captured(1));
            if (match.hasMatch() && url.isValid() && !m_locations.contains(url)) {
                m_locations << url;
            }
        }
    });
    // Windows sends multicast out of a single, often virtual, adapter (WSL, Hyper-V, VPN), so ask on every one.
    // Both IGD versions and the services are asked for directly; some routers only answer one of these.
    for (const auto& iface : QNetworkInterface::allInterfaces()) {
        const auto flags = iface.flags();
        if (!(flags & QNetworkInterface::IsUp) || !(flags & QNetworkInterface::CanMulticast) || (flags & QNetworkInterface::IsLoopBack)) {
            continue;
        }
        bool hasIpv4 = false;
        for (const auto& entry : iface.addressEntries()) {
            hasIpv4 = hasIpv4 || entry.ip().protocol() == QAbstractSocket::IPv4Protocol;
        }
        if (!hasIpv4) {
            continue;
        }
        m_socket->setMulticastInterface(iface);
        for (const char* target : { "urn:schemas-upnp-org:device:InternetGatewayDevice:1", "urn:schemas-upnp-org:device:InternetGatewayDevice:2",
                                    "urn:schemas-upnp-org:service:WANIPConnection:1", "urn:schemas-upnp-org:service:WANPPPConnection:1" }) {
            const QByteArray search = QByteArray("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ") +
                                      target + "\r\n\r\n";
            m_socket->writeDatagram(search, kSsdpGroup, kSsdpPort);
        }
    }
    m_searchTimer.start(kSearchMs);
}

void PortMapper::searchFinished()
{
    if (m_socket) {
        m_socket->deleteLater();
        m_socket = nullptr;
    }
    fetchDescription(m_locations, 0);
}

void PortMapper::fetchDescription(const QList<QUrl>& locations, int index)
{
    if (index >= locations.size()) {
        // nothing usable found (or every candidate failed): tell everyone who waited
        m_searching = false;
        const auto waiting = m_waiting;
        m_waiting.clear();
        for (const auto& then : waiting) {
            then();
        }
        return;
    }
    QNetworkRequest request(locations[index]);
    request.setTransferTimeout(kSoapTimeoutMs);
    QNetworkReply* reply = m_network->get(request);
    connect(reply, &QNetworkReply::finished, this, [this, reply, locations, index] {
        reply->deleteLater();
        if (reply->error() != QNetworkReply::NoError) {
            fetchDescription(locations, index + 1);
            return;
        }
        if (!parseDescription(reply->readAll(), locations[index], &m_gateway.controlUrl, &m_gateway.serviceType)) {
            fetchDescription(locations, index + 1);
            return;
        }
        m_gateway.localAddress = localAddressFor(QHostAddress(m_gateway.controlUrl.host()));
        fetchDescription({}, 0);  // done: run the waiting callbacks
    });
}

void PortMapper::addMapping(int port, int lease)
{
    QNetworkReply* reply = soap(QStringLiteral("AddPortMapping"),
                                { { QStringLiteral("NewRemoteHost"), QString() },
                                  { QStringLiteral("NewExternalPort"), QString::number(port) },
                                  { QStringLiteral("NewProtocol"), QStringLiteral("TCP") },
                                  { QStringLiteral("NewInternalPort"), QString::number(port) },
                                  { QStringLiteral("NewInternalClient"), m_gateway.localAddress.toString() },
                                  { QStringLiteral("NewEnabled"), QStringLiteral("1") },
                                  { QStringLiteral("NewPortMappingDescription"), m_descriptions.value(port, QStringLiteral("Yahu Client")) },
                                  { QStringLiteral("NewLeaseDuration"), QString::number(lease) } });
    connect(reply, &QNetworkReply::finished, this, [this, reply, port, lease] {
        reply->deleteLater();
        const QByteArray body = reply->readAll();
        const State now = m_mappings.value(port).state;
        if (now != State::Opening && now != State::Open) {
            // the server stopped in the meantime; take back a mapping the router made anyway
            if (reply->error() == QNetworkReply::NoError) {
                auto* undo = soap(QStringLiteral("DeletePortMapping"), { { QStringLiteral("NewRemoteHost"), QString() },
                                                                         { QStringLiteral("NewExternalPort"), QString::number(port) },
                                                                         { QStringLiteral("NewProtocol"), QStringLiteral("TCP") } });
                connect(undo, &QNetworkReply::finished, undo, &QObject::deleteLater);
            }
            return;
        }
        if (reply->error() == QNetworkReply::NoError) {
            m_renewTimer.start();
            fetchExternalAddress(port);
            return;
        }
        const QString error = soapError(reply, body);
        if (error.startsWith(QString::number(kOnlyPermanentLeasesSupported)) && lease != 0) {
            addMapping(port, 0);  // older routers only accept mappings without an expiry
            return;
        }
        if (error.startsWith(QString::number(kConflictInMappingEntry))) {
            setMapping(port, State::Refused, QString(),
                       tr("Another device in your network already uses port %1 on the router. Pick a different port in the "
                          "server settings.")
                           .arg(port));
            return;
        }
        if (reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).isNull()) {
            // no answer at all: the router went away or changed, look for it again next time
            m_gateway = {};
            setMapping(port, State::NoRouter, QString(), tr("The router stopped answering."));
            return;
        }
        setMapping(port, State::Refused, QString(), tr("The router didn't allow it (%1).").arg(error));
    });
}

void PortMapper::fetchExternalAddress(int port)
{
    QNetworkReply* reply = soap(QStringLiteral("GetExternalIPAddress"), {});
    connect(reply, &QNetworkReply::finished, this, [this, reply, port] {
        reply->deleteLater();
        const State now = m_mappings.value(port).state;
        if (now != State::Opening && now != State::Open) {
            return;
        }
        static const QRegularExpression ipTag(QStringLiteral("<NewExternalIPAddress>([^<]*)</NewExternalIPAddress>"));
        const QString ip = ipTag.match(QString::fromUtf8(reply->readAll())).captured(1).trimmed();
        if (reply->error() != QNetworkReply::NoError || ip.isEmpty()) {
            // the port is open, the router just won't tell its address; the page can still look it up
            setMapping(port, State::Open);
            return;
        }
        if (isSharedAddress(QHostAddress(ip))) {
            setMapping(port, State::SharedAddress, ip,
                       tr("Your internet connection has no public address of its own (the provider shares one between many "
                          "customers, or there is a second router in front of this one), so friends can't reach this PC "
                          "directly."));
            return;
        }
        setMapping(port, State::Open, ip);
    });
}

void PortMapper::setMapping(int port, State state, const QString& externalAddress, const QString& detail)
{
    Mapping& m = m_mappings[port];
    if (m.state == state && m.externalAddress == externalAddress && m.detail == detail) {
        return;
    }
    m = { state, externalAddress, detail };
    emit mappingChanged(port);
}

QNetworkReply* PortMapper::soap(const QString& action, const QList<QPair<QString, QString>>& args)
{
    QString body = QStringLiteral(
                       "<?xml version=\"1.0\"?>\r\n"
                       "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                       "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:%1 xmlns:u=\"%2\">")
                       .arg(action, m_gateway.serviceType);
    for (const auto& [name, value] : args) {
        body += QStringLiteral("<%1>%2</%1>").arg(name, value.toHtmlEscaped());
    }
    body += QStringLiteral("</u:%1></s:Body></s:Envelope>\r\n").arg(action);

    QNetworkRequest request(m_gateway.controlUrl);
    request.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("text/xml; charset=\"utf-8\""));
    request.setRawHeader("SOAPAction", QStringLiteral("\"%1#%2\"").arg(m_gateway.serviceType, action).toUtf8());
    request.setTransferTimeout(kSoapTimeoutMs);
    return m_network->post(request, body.toUtf8());
}

QString PortMapper::soapError(QNetworkReply* reply, const QByteArray& body)
{
    const QString fault = faultFromBody(body);
    return fault.isEmpty() ? reply->errorString() : fault;
}

QHostAddress PortMapper::localAddressFor(const QHostAddress& router)
{
    // the address of the network card that sits in the same subnet as the router
    QHostAddress fallback;
    for (const auto& iface : QNetworkInterface::allInterfaces()) {
        if (!(iface.flags() & QNetworkInterface::IsUp) || (iface.flags() & QNetworkInterface::IsLoopBack)) {
            continue;
        }
        for (const auto& entry : iface.addressEntries()) {
            const QHostAddress ip = entry.ip();
            if (ip.protocol() != QAbstractSocket::IPv4Protocol || ip.isLoopback()) {
                continue;
            }
            if (ip.isInSubnet(router, entry.prefixLength())) {
                return ip;
            }
            if (fallback.isNull() && ip.isPrivateUse()) {
                fallback = ip;
            }
        }
    }
    return fallback;
}
