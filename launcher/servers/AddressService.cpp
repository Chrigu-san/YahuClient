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

#include "AddressService.h"

#include <QJsonDocument>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QRegularExpression>
#include <QSet>

#include "servers/ServerManager.h"

const QString AddressService::kDomain = QStringLiteral("yahuclient.net");

namespace {
constexpr int kTimeoutMs = 10000;

// keep in sync with RESERVED in services/dns-worker/src/index.js
const QSet<QString> kReserved = { "www",     "api",   "mail",      "smtp",   "imap",      "pop",     "ftp",     "ns",    "ns1",
                                  "ns2",     "relay", "play",      "mc",     "server",    "servers", "admin",   "root",  "support",
                                  "help",    "status", "download", "downloads", "dev",    "test",    "staging", "yahu",  "yahuclient",
                                  "shop",    "store", "auth",      "login",  "cdn",       "static",  "assets",  "docs",  "blog",
                                  "news",    "discord" };
}  // namespace

AddressService::AddressService(const QUrl& api, QObject* parent) : QObject(parent), m_api(api), m_network(new QNetworkAccessManager(this))
{}

bool AddressService::isValidName(const QString& name)
{
    static const QRegularExpression label(QStringLiteral("^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$"));
    return label.match(name).hasMatch() && !name.contains(QLatin1String("--")) && !kReserved.contains(name);
}

QString AddressService::suggestName(const QString& text)
{
    // "Freunde Survival" -> "freunde-survival", "Pingu_Shrek" -> "pingu-shrek"
    QString name = text.toLower();
    name.replace(QRegularExpression(QStringLiteral("[^a-z0-9]+")), QStringLiteral("-"));
    name = name.section('-', 0, -1, QString::SectionSkipEmpty).left(32);
    while (name.endsWith('-')) {
        name.chop(1);
    }
    return isValidName(name) ? name : QString();
}

void AddressService::publish(YahuServer* server, const QString& externalIp)
{
    const QString name = server->config().address;
    if (name.isEmpty()) {
        setStatus(server, State::None);
        return;
    }
    if (!isValidName(name)) {
        setStatus(server, State::Failed, QString(), tr("\"%1\" can't be used as an address. Pick another one in the server settings.").arg(name));
        return;
    }
    if (m_status.value(server).state == State::Working) {
        return;
    }
    setStatus(server, State::Working, QStringLiteral("%1.%2").arg(name, kDomain));
    if (server->config().addressSecret.isEmpty()) {
        claim(server, externalIp);
    } else {
        update(server, externalIp);
    }
}

void AddressService::claim(YahuServer* server, const QString& externalIp)
{
    const QString name = server->config().address;
    post(QStringLiteral("/v1/claim"), { { "name", name } }, server, [this, server, name, externalIp](int status, const QJsonObject& reply) {
        if (server->config().address != name) {
            return;  // renamed in the meantime
        }
        if (status == 201 && !reply["secret"].toString().isEmpty()) {
            ServerConfig config = server->config();
            config.addressSecret = reply["secret"].toString();
            server->setConfig(config);
            update(server, externalIp);
        } else if (status == 409) {
            setStatus(server, State::Taken, QString(),
                      tr("Somebody already has %1.%2. Pick another name in the server settings.").arg(name, kDomain));
        } else {
            setStatus(server, State::Failed, QString(), tr("The address service didn't answer (%1).").arg(status ? QString::number(status) : tr("offline")));
        }
    });
}

void AddressService::update(YahuServer* server, const QString& externalIp)
{
    const ServerConfig& c = server->config();
    QJsonObject body{ { "name", c.address }, { "secret", c.addressSecret }, { "port", c.port } };
    if (!externalIp.isEmpty()) {
        body["ip"] = externalIp;
    }
    const QString name = c.address;
    post(QStringLiteral("/v1/update"), body, server, [this, server, name, externalIp](int status, const QJsonObject& reply) {
        if (server->config().address != name) {
            return;
        }
        if (status == 200) {
            setStatus(server, State::Ready, reply["address"].toString(QStringLiteral("%1.%2").arg(name, kDomain)));
        } else if (status == 403 && reply["error"].toString() == QLatin1String("forbidden")) {
            // the secret doesn't fit (name released or settings copied from elsewhere): try to claim it fresh
            ServerConfig config = server->config();
            config.addressSecret.clear();
            server->setConfig(config);
            claim(server, externalIp);
        } else if (reply["error"].toString() == QLatin1String("invalid_ip")) {
            setStatus(server, State::Failed, QString(), tr("This connection has no public address the name could point to."));
        } else {
            setStatus(server, State::Failed, QString(), tr("The address service didn't answer (%1).").arg(status ? QString::number(status) : tr("offline")));
        }
    });
}

void AddressService::release(const QString& name, const QString& secret)
{
    if (name.isEmpty() || secret.isEmpty()) {
        return;
    }
    post(QStringLiteral("/v1/release"), { { "name", name }, { "secret", secret } }, this, [](int, const QJsonObject&) {});
}

void AddressService::setStatus(YahuServer* server, State state, const QString& address, const QString& detail)
{
    Status& s = m_status[server];
    if (s.state == state && s.address == address && s.detail == detail) {
        return;
    }
    s = { state, address, detail };
    emit statusChanged(server);
}

void AddressService::post(const QString& path, const QJsonObject& body, QObject* guard, std::function<void(int, const QJsonObject&)> done)
{
    QUrl url = m_api;
    url.setPath(path);
    QNetworkRequest request(url);
    request.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/json"));
    request.setHeader(QNetworkRequest::UserAgentHeader, QStringLiteral("YahuClient"));
    request.setTransferTimeout(kTimeoutMs);
    QNetworkReply* reply = m_network->post(request, QJsonDocument(body).toJson(QJsonDocument::Compact));
    QPointer<QObject> alive(guard);
    connect(reply, &QNetworkReply::finished, this, [reply, alive, done] {
        reply->deleteLater();
        if (!alive) {
            return;  // the server was deleted while we waited
        }
        const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        done(status, QJsonDocument::fromJson(reply->readAll()).object());
    });
}
