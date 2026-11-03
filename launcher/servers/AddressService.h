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

#pragma once

#include <QHash>
#include <QJsonObject>
#include <QObject>
#include <QPointer>
#include <QUrl>
#include <functional>

class QNetworkAccessManager;
class YahuServer;

// Gives a server a fixed address like chris.yahuclient.net. Talks to the Yahu address service
// (services/dns-worker), which owns the DNS zone: a name is claimed once (the secret is kept in the server's
// settings) and pointed at the player's public IP every time the server is reachable.
class AddressService : public QObject {
    Q_OBJECT
   public:
    enum class State { None, Working, Ready, Taken, Failed };

    struct Status {
        State state = State::None;
        QString address;  // e.g. chris.yahuclient.net
        QString detail;   // why it didn't work, in words for the player
    };

    static const QString kDomain;  // yahuclient.net

    explicit AddressService(const QUrl& api = QUrl(QStringLiteral("https://api.yahuclient.net")), QObject* parent = nullptr);

    // 3 to 32 characters: lowercase letters, digits and single inner dashes; some names are reserved.
    static bool isValidName(const QString& name);
    // A usable name made from free text (a player or server name), or empty if nothing usable is left.
    static QString suggestName(const QString& text);

    // Points the server's name at this PC. externalIp is the router's public IPv4 if known (needed over IPv6).
    void publish(YahuServer* server, const QString& externalIp);
    // Gives a name up, e.g. after the player picked another one. Fire and forget.
    void release(const QString& name, const QString& secret);

    Status status(YahuServer* server) const { return m_status.value(server); }

   signals:
    void statusChanged(YahuServer* server);

   private:
    void claim(YahuServer* server, const QString& externalIp);
    void update(YahuServer* server, const QString& externalIp);
    void setStatus(YahuServer* server, State state, const QString& address = QString(), const QString& detail = QString());
    void post(const QString& path, const QJsonObject& body, QObject* guard, std::function<void(int, const QJsonObject&)> done);

    QUrl m_api;
    QNetworkAccessManager* m_network;
    QHash<YahuServer*, Status> m_status;
};
