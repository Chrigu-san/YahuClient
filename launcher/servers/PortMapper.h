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
#include <QHostAddress>
#include <QObject>
#include <QTimer>
#include <QUrl>
#include <functional>

class QNetworkAccessManager;
class QNetworkReply;
class QUdpSocket;

// Opens TCP ports on the home router via UPnP (Internet Gateway Device), so friends can join a server over the
// internet without anyone setting up port forwarding by hand. Works with most home routers; when the router has
// UPnP switched off, or the provider hands out no public address (CGNAT), the mapping state says so.
class PortMapper : public QObject {
    Q_OBJECT
   public:
    enum class State { Closed, Opening, Open, NoRouter, Refused, SharedAddress };

    struct Mapping {
        State state = State::Closed;
        QString externalAddress;  // the router's public IP once the port is open
        QString detail;           // why it failed, in words for the player
    };

    explicit PortMapper(QObject* parent = nullptr);
    ~PortMapper() override;

    void open(int port, const QString& description);
    void close(int port);
    // Removes every mapping, waiting up to waitMs for the router to answer (used when the launcher quits).
    void closeAll(int waitMs);

    Mapping mapping(int port) const { return m_mappings.value(port); }

   signals:
    void mappingChanged(int port);

   private:
    struct Gateway {
        QUrl controlUrl;
        QString serviceType;
        QHostAddress localAddress;  // our address in the router's network
    };

    void discover(std::function<void()> then);
    void searchFinished();
    void fetchDescription(const QList<QUrl>& locations, int index);
    void addMapping(int port, int lease);
    void fetchExternalAddress(int port);
    void setMapping(int port, State state, const QString& externalAddress = QString(), const QString& detail = QString());
    QNetworkReply* soap(const QString& action, const QList<QPair<QString, QString>>& args);
    static QString soapError(QNetworkReply* reply, const QByteArray& body);
    static QHostAddress localAddressFor(const QHostAddress& router);

    QNetworkAccessManager* m_network;
    QUdpSocket* m_socket = nullptr;
    QTimer m_searchTimer;
    QTimer m_renewTimer;
    QList<QUrl> m_locations;
    QList<std::function<void()>> m_waiting;
    bool m_searching = false;
    Gateway m_gateway;
    QHash<int, Mapping> m_mappings;
    QHash<int, QString> m_descriptions;
};
