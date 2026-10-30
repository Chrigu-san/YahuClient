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

#include <QList>
#include <QMetaObject>
#include <QPointer>
#include <QWidget>

class QLabel;
class QLineEdit;
class QListWidget;
class QPlainTextEdit;
class QPushButton;
class QStackedWidget;
class ServerManager;
class YahuServer;

// The Servers tab: your servers on the left; status, join address, console and controls on the right.
class YahuServersPage : public QWidget {
    Q_OBJECT
   public:
    explicit YahuServersPage(ServerManager* manager, QWidget* parent = nullptr);

   private:
    void createServer();
    void editServer();
    void deleteServer();
    void rebuildList();
    void select(YahuServer* server);
    void refreshDetail();
    void refreshAddresses();
    void refreshPlayers();
    QString withPort(const QString& host) const;

    ServerManager* m_manager;
    QPointer<YahuServer> m_current;
    QList<QMetaObject::Connection> m_serverConnections;

    QListWidget* m_list;
    QStackedWidget* m_stack;

    QLabel* m_name;
    QLabel* m_subtitle;
    QLabel* m_status;
    QLabel* m_detail;
    QPushButton* m_startStop;
    QPushButton* m_settings;
    QPushButton* m_folder;
    QPushButton* m_delete;

    QLabel* m_lanAddress;
    QLabel* m_publicAddress;
    QPushButton* m_copyLan;
    QPushButton* m_publicButton;
    QLabel* m_players;

    QPlainTextEdit* m_console;
    QLineEdit* m_command;
};
