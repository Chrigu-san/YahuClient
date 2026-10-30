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

#include <QJsonObject>
#include <QList>
#include <QObject>
#include <QPointer>
#include <QProcess>
#include <QSet>
#include <QStringList>
#include <QTimer>
#include <functional>

class QNetworkReply;

// Everything the user can choose when creating or editing a server. Most fields map 1:1 to server.properties.
struct ServerConfig {
    enum class Software { Paper, Vanilla, Fabric };

    QString name = QStringLiteral("My server");
    Software software = Software::Paper;
    QString mcVersion;
    int memoryMb = 4096;
    int port = 25565;
    QString motd = QStringLiteral("A Yahu Client server");

    // world
    QString levelName = QStringLiteral("world");
    QString seed;
    QString levelType = QStringLiteral("minecraft:normal");
    QString gamemode = QStringLiteral("survival");
    QString difficulty = QStringLiteral("normal");
    bool hardcore = false;
    bool allowNether = true;
    bool generateStructures = true;
    int spawnProtection = 16;

    // players
    int maxPlayers = 10;
    bool onlineMode = true;
    bool whitelist = false;
    bool pvp = true;
    bool allowFlight = false;
    bool forceGamemode = false;
    int viewDistance = 10;
    int simulationDistance = 10;
    int idleTimeout = 0;

    // gameplay and advanced
    bool spawnMonsters = true;
    bool spawnAnimals = true;
    bool spawnNpcs = true;
    bool commandBlocks = false;
    bool enforceSecureProfile = true;
    QString javaPath;  // empty: pick automatically
    QString jvmArgs;

    // filled in by the installer
    int javaMajor = 0;
    QString installedSoftware;  // what server.jar currently is, e.g. "paper 1.21.4"

    QJsonObject toJson() const;
    static ServerConfig fromJson(const QJsonObject& json);
    static QString softwareId(Software software);
    static QString softwareName(Software software);
    QString installKey() const;  // changes whenever a different server.jar is needed
};

// One server on this PC: its folder, settings and (when running) its Java process.
class YahuServer : public QObject {
    Q_OBJECT
   public:
    enum class State { Stopped, Installing, Starting, Running, Stopping, Failed };

    YahuServer(const QString& dir, const ServerConfig& config, QObject* parent = nullptr);
    ~YahuServer() override;

    QString dir() const { return m_dir; }
    const ServerConfig& config() const { return m_config; }
    // Saves the settings and rewrites server.properties (keeping keys we don't manage).
    void setConfig(const ServerConfig& config);

    State state() const { return m_state; }
    bool isBusy() const { return m_state != State::Stopped && m_state != State::Failed; }
    QString stateText() const;
    QString detail() const { return m_detail; }  // install progress or the reason it failed
    bool isInstalled() const;

    QStringList log() const { return m_log; }
    QStringList players() const;

    void start(const QString& javaPath);
    void stop();
    bool waitForExit(int ms);
    void sendCommand(const QString& command);

    void setInstalling(const QString& detail);
    void setFailed(const QString& reason);
    void setIdle();

    void writeProperties() const;
    void appendLog(const QString& line);

   signals:
    void stateChanged();
    void logAppended(const QString& line);
    void playersChanged();

   private:
    void setState(State state, const QString& detail = QString());
    void readOutput();

    QString m_dir;
    ServerConfig m_config;
    State m_state = State::Stopped;
    QString m_detail;
    QStringList m_log;
    QSet<QString> m_players;
    QProcess* m_process = nullptr;
    QString m_partialLine;
    bool m_stopRequested = false;
    QTimer m_killTimer;
};

// Keeps track of all servers in <data>/servers, downloads server software and finds a fitting Java.
class ServerManager : public QObject {
    Q_OBJECT
   public:
    struct McVersion {
        QString id;
        bool release = true;
    };
    struct JavaFound {
        QString path;
        int major = 0;
    };

    explicit ServerManager(const QString& root, QObject* parent = nullptr);
    ~ServerManager() override;

    QList<YahuServer*> servers() const { return m_servers; }

    // Creates the folder, then downloads the server software in the background.
    YahuServer* create(const ServerConfig& config, const QString& operatorName, const QString& operatorUuid);
    // Applies edited settings; downloads new software first if the type or version changed.
    void update(YahuServer* server, const ServerConfig& config);
    // Moves the server folder to the recycle bin.
    bool remove(YahuServer* server, QString* error);
    void start(YahuServer* server);
    // Asks every running server to save and stop, waiting up to waitMs in total.
    void stopAll(int waitMs);

    int suggestPort() const;

    // Minecraft versions, newest first (loaded once from Mojang's version list).
    void loadVersions();
    QList<McVersion> versions() const { return m_versions; }
    bool versionsLoaded() const { return !m_versions.isEmpty(); }

    QList<JavaFound> javas();
    QString pickJava(const ServerConfig& config, QString* problem);

    static QString lanAddress();
    void fetchPublicAddress();
    QString publicAddress() const { return m_publicAddress; }

   signals:
    void serversChanged();
    void versionsChanged(const QString& error);
    void publicAddressChanged(const QString& error);

   private:
    void load();
    void install(YahuServer* server);
    QNetworkReply* get(const QString& url);
    // GETs JSON, calling done(json, error) when finished. The guard object stops the chain if it is deleted.
    void getJson(const QString& url, QObject* guard, std::function<void(const QJsonDocument&, const QString&)> done);

    QString m_root;
    QList<YahuServer*> m_servers;
    QList<McVersion> m_versions;
    QJsonObject m_manifest;
    bool m_loadingVersions = false;
    QList<JavaFound> m_javas;
    bool m_javasScanned = false;
    QString m_publicAddress;
};
