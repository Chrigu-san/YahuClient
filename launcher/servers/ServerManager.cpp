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

#include "ServerManager.h"

#include <QDir>
#include <QElapsedTimer>
#include <QFile>
#include <QFileInfo>
#include <QHostAddress>
#include <QJsonArray>
#include <QJsonDocument>
#include <QNetworkInterface>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QRegularExpression>
#include <QSaveFile>
#include <QTextStream>

#include "Application.h"
#include "FileSystem.h"
#include "java/JavaUtils.h"

namespace {
const QString kConfigFile = QStringLiteral("yahu-server.json");
const QString kJar = QStringLiteral("server.jar");
const QString kManifestUrl = QStringLiteral("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
constexpr int kMaxLogLines = 1500;

// Java .properties escaping: backslashes, line breaks and everything outside ASCII.
QString escapeProperty(const QString& value)
{
    QString out;
    for (const QChar c : value) {
        if (c == '\\') {
            out += QStringLiteral("\\\\");
        } else if (c == '\n') {
            out += QStringLiteral("\\n");
        } else if (c.unicode() > 126) {
            out += QStringLiteral("\\u%1").arg(int(c.unicode()), 4, 16, QChar('0'));
        } else {
            out += c;
        }
    }
    return out;
}

QString dashedUuid(QString id)
{
    id.remove('-');
    if (id.size() != 32) {
        return id;
    }
    return id.left(8) + '-' + id.mid(8, 4) + '-' + id.mid(12, 4) + '-' + id.mid(16, 4) + '-' + id.mid(20);
}

// Reads the major version from a Java installation's "release" file (JAVA_VERSION="21.0.2" or "1.8.0_392").
int javaMajorAt(const QString& executable)
{
    QDir home = QFileInfo(executable).dir();
    home.cdUp();
    QFile release(home.filePath(QStringLiteral("release")));
    if (!release.open(QIODevice::ReadOnly | QIODevice::Text)) {
        return 0;
    }
    static const QRegularExpression re(QStringLiteral(R"(JAVA_VERSION="(\d+)(?:\.(\d+))?)"));
    const auto match = re.match(QString::fromUtf8(release.readAll()));
    if (!match.hasMatch()) {
        return 0;
    }
    const int first = match.captured(1).toInt();
    return first == 1 ? match.captured(2).toInt() : first;
}
}  // namespace

// ---------------------------------------------------------------------------------------------------------------------
// ServerConfig

QString ServerConfig::softwareId(Software software)
{
    switch (software) {
        case Software::Vanilla:
            return QStringLiteral("vanilla");
        case Software::Fabric:
            return QStringLiteral("fabric");
        case Software::Paper:
        default:
            return QStringLiteral("paper");
    }
}

QString ServerConfig::softwareName(Software software)
{
    switch (software) {
        case Software::Vanilla:
            return QObject::tr("Vanilla");
        case Software::Fabric:
            return QObject::tr("Fabric");
        case Software::Paper:
        default:
            return QObject::tr("Paper");
    }
}

QString ServerConfig::installKey() const
{
    return softwareId(software) + ' ' + mcVersion;
}

QJsonObject ServerConfig::toJson() const
{
    QJsonObject o;
    o["name"] = name;
    o["software"] = softwareId(software);
    o["mcVersion"] = mcVersion;
    o["memoryMb"] = memoryMb;
    o["port"] = port;
    o["motd"] = motd;
    o["levelName"] = levelName;
    o["seed"] = seed;
    o["levelType"] = levelType;
    o["gamemode"] = gamemode;
    o["difficulty"] = difficulty;
    o["hardcore"] = hardcore;
    o["allowNether"] = allowNether;
    o["generateStructures"] = generateStructures;
    o["spawnProtection"] = spawnProtection;
    o["maxPlayers"] = maxPlayers;
    o["onlineMode"] = onlineMode;
    o["whitelist"] = whitelist;
    o["pvp"] = pvp;
    o["allowFlight"] = allowFlight;
    o["forceGamemode"] = forceGamemode;
    o["viewDistance"] = viewDistance;
    o["simulationDistance"] = simulationDistance;
    o["idleTimeout"] = idleTimeout;
    o["spawnMonsters"] = spawnMonsters;
    o["spawnAnimals"] = spawnAnimals;
    o["spawnNpcs"] = spawnNpcs;
    o["commandBlocks"] = commandBlocks;
    o["enforceSecureProfile"] = enforceSecureProfile;
    o["javaPath"] = javaPath;
    o["jvmArgs"] = jvmArgs;
    o["javaMajor"] = javaMajor;
    o["installedSoftware"] = installedSoftware;
    return o;
}

ServerConfig ServerConfig::fromJson(const QJsonObject& o)
{
    ServerConfig c;
    c.name = o["name"].toString(c.name);
    const QString software = o["software"].toString();
    c.software = software == "vanilla" ? Software::Vanilla : (software == "fabric" ? Software::Fabric : Software::Paper);
    c.mcVersion = o["mcVersion"].toString();
    c.memoryMb = o["memoryMb"].toInt(c.memoryMb);
    c.port = o["port"].toInt(c.port);
    c.motd = o["motd"].toString(c.motd);
    c.levelName = o["levelName"].toString(c.levelName);
    c.seed = o["seed"].toString();
    c.levelType = o["levelType"].toString(c.levelType);
    c.gamemode = o["gamemode"].toString(c.gamemode);
    c.difficulty = o["difficulty"].toString(c.difficulty);
    c.hardcore = o["hardcore"].toBool(c.hardcore);
    c.allowNether = o["allowNether"].toBool(c.allowNether);
    c.generateStructures = o["generateStructures"].toBool(c.generateStructures);
    c.spawnProtection = o["spawnProtection"].toInt(c.spawnProtection);
    c.maxPlayers = o["maxPlayers"].toInt(c.maxPlayers);
    c.onlineMode = o["onlineMode"].toBool(c.onlineMode);
    c.whitelist = o["whitelist"].toBool(c.whitelist);
    c.pvp = o["pvp"].toBool(c.pvp);
    c.allowFlight = o["allowFlight"].toBool(c.allowFlight);
    c.forceGamemode = o["forceGamemode"].toBool(c.forceGamemode);
    c.viewDistance = o["viewDistance"].toInt(c.viewDistance);
    c.simulationDistance = o["simulationDistance"].toInt(c.simulationDistance);
    c.idleTimeout = o["idleTimeout"].toInt(c.idleTimeout);
    c.spawnMonsters = o["spawnMonsters"].toBool(c.spawnMonsters);
    c.spawnAnimals = o["spawnAnimals"].toBool(c.spawnAnimals);
    c.spawnNpcs = o["spawnNpcs"].toBool(c.spawnNpcs);
    c.commandBlocks = o["commandBlocks"].toBool(c.commandBlocks);
    c.enforceSecureProfile = o["enforceSecureProfile"].toBool(c.enforceSecureProfile);
    c.javaPath = o["javaPath"].toString();
    c.jvmArgs = o["jvmArgs"].toString();
    c.javaMajor = o["javaMajor"].toInt();
    c.installedSoftware = o["installedSoftware"].toString();
    return c;
}

// ---------------------------------------------------------------------------------------------------------------------
// YahuServer

YahuServer::YahuServer(const QString& dir, const ServerConfig& config, QObject* parent) : QObject(parent), m_dir(dir), m_config(config)
{
    m_killTimer.setSingleShot(true);
    connect(&m_killTimer, &QTimer::timeout, this, [this] {
        if (m_process && m_process->state() != QProcess::NotRunning) {
            appendLog(tr("[Yahu] The server didn't stop in time, ending it."));
            m_process->kill();
        }
    });
}

YahuServer::~YahuServer()
{
    if (m_process && m_process->state() != QProcess::NotRunning) {
        m_process->kill();
        m_process->waitForFinished(3000);
    }
}

void YahuServer::setConfig(const ServerConfig& config)
{
    m_config = config;
    QSaveFile file(QDir(m_dir).filePath(kConfigFile));
    if (file.open(QIODevice::WriteOnly)) {
        file.write(QJsonDocument(m_config.toJson()).toJson());
        file.commit();
    }
    writeProperties();
}

bool YahuServer::isInstalled() const
{
    return QFileInfo::exists(QDir(m_dir).filePath(kJar)) && m_config.installedSoftware == m_config.installKey();
}

QString YahuServer::stateText() const
{
    switch (m_state) {
        case State::Installing:
            return tr("Installing");
        case State::Starting:
            return tr("Starting");
        case State::Running:
            return tr("Running");
        case State::Stopping:
            return tr("Stopping");
        case State::Failed:
            return tr("Problem");
        case State::Stopped:
        default:
            return tr("Offline");
    }
}

QStringList YahuServer::players() const
{
    QStringList list(m_players.begin(), m_players.end());
    list.sort(Qt::CaseInsensitive);
    return list;
}

void YahuServer::setState(State state, const QString& detail)
{
    m_state = state;
    m_detail = detail;
    emit stateChanged();
}

void YahuServer::setInstalling(const QString& detail)
{
    setState(State::Installing, detail);
}

void YahuServer::setFailed(const QString& reason)
{
    appendLog(QStringLiteral("[Yahu] ") + reason);
    setState(State::Failed, reason);
}

void YahuServer::setIdle()
{
    setState(State::Stopped);
}

void YahuServer::appendLog(const QString& line)
{
    m_log.append(line);
    if (m_log.size() > kMaxLogLines) {
        m_log.erase(m_log.begin(), m_log.begin() + (m_log.size() - kMaxLogLines));
    }
    emit logAppended(line);
}

void YahuServer::writeProperties() const
{
    const QString path = QDir(m_dir).filePath(QStringLiteral("server.properties"));
    const ServerConfig& c = m_config;
    const QList<QPair<QString, QString>> managed = {
        { "server-port", QString::number(c.port) },
        { "query.port", QString::number(c.port) },
        { "motd", c.motd },
        { "max-players", QString::number(c.maxPlayers) },
        { "online-mode", c.onlineMode ? "true" : "false" },
        { "white-list", c.whitelist ? "true" : "false" },
        { "enforce-whitelist", c.whitelist ? "true" : "false" },
        { "pvp", c.pvp ? "true" : "false" },
        { "allow-flight", c.allowFlight ? "true" : "false" },
        { "force-gamemode", c.forceGamemode ? "true" : "false" },
        { "view-distance", QString::number(c.viewDistance) },
        { "simulation-distance", QString::number(c.simulationDistance) },
        { "player-idle-timeout", QString::number(c.idleTimeout) },
        { "level-name", c.levelName },
        { "level-seed", c.seed },
        { "level-type", c.levelType },
        { "gamemode", c.gamemode },
        { "difficulty", c.difficulty },
        { "hardcore", c.hardcore ? "true" : "false" },
        { "allow-nether", c.allowNether ? "true" : "false" },
        { "generate-structures", c.generateStructures ? "true" : "false" },
        { "spawn-protection", QString::number(c.spawnProtection) },
        { "spawn-monsters", c.spawnMonsters ? "true" : "false" },
        { "spawn-animals", c.spawnAnimals ? "true" : "false" },
        { "spawn-npcs", c.spawnNpcs ? "true" : "false" },
        { "enable-command-block", c.commandBlocks ? "true" : "false" },
        { "enforce-secure-profile", c.enforceSecureProfile ? "true" : "false" },
    };

    // keep whatever else is in the file (the server adds its own keys on first start)
    QStringList lines;
    QFile in(path);
    if (in.open(QIODevice::ReadOnly | QIODevice::Text)) {
        lines = QString::fromUtf8(in.readAll()).split('\n');
        in.close();
    }
    QSet<QString> written;
    for (QString& line : lines) {
        const QString trimmed = line.trimmed();
        if (trimmed.startsWith('#') || !trimmed.contains('=')) {
            continue;
        }
        const QString key = trimmed.section('=', 0, 0).trimmed();
        for (const auto& [k, v] : managed) {
            if (k == key) {
                line = k + '=' + escapeProperty(v);
                written.insert(k);
            }
        }
    }
    while (!lines.isEmpty() && lines.last().trimmed().isEmpty()) {
        lines.removeLast();
    }
    if (lines.isEmpty()) {
        lines << QStringLiteral("# Minecraft server properties, managed by Yahu Client");
    }
    for (const auto& [k, v] : managed) {
        if (!written.contains(k)) {
            lines << k + '=' + escapeProperty(v);
        }
    }
    QSaveFile out(path);
    if (out.open(QIODevice::WriteOnly)) {
        out.write((lines.join('\n') + '\n').toUtf8());
        out.commit();
    }
}

void YahuServer::start(const QString& javaPath)
{
    if (m_process && m_process->state() != QProcess::NotRunning) {
        return;
    }
    writeProperties();
    m_players.clear();
    emit playersChanged();
    m_stopRequested = false;
    m_partialLine.clear();

    delete m_process;
    m_process = new QProcess(this);
    m_process->setWorkingDirectory(m_dir);
    m_process->setProcessChannelMode(QProcess::MergedChannels);
    connect(m_process, &QProcess::readyRead, this, &YahuServer::readOutput);
    connect(m_process, &QProcess::errorOccurred, this, [this](QProcess::ProcessError error) {
        if (error == QProcess::FailedToStart) {
            setFailed(tr("Java could not be started: %1").arg(m_process->errorString()));
        }
    });
    connect(m_process, &QProcess::finished, this, [this](int code, QProcess::ExitStatus) {
        m_killTimer.stop();
        readOutput();
        m_players.clear();
        emit playersChanged();
        if (m_stopRequested || code == 0) {
            appendLog(tr("[Yahu] Server stopped."));
            setState(State::Stopped);
        } else {
            setFailed(tr("The server stopped unexpectedly (exit code %1). The console above usually says why.").arg(code));
        }
    });

    QStringList args;
    args << QStringLiteral("-Xms%1M").arg(qMin(1024, m_config.memoryMb)) << QStringLiteral("-Xmx%1M").arg(m_config.memoryMb);
    args << QProcess::splitCommand(m_config.jvmArgs);
    args << QStringLiteral("-jar") << kJar << QStringLiteral("nogui");
    appendLog(tr("[Yahu] Starting with %1").arg(javaPath));
    setState(State::Starting);
    m_process->start(javaPath, args);
}

void YahuServer::stop()
{
    if (!m_process || m_process->state() == QProcess::NotRunning) {
        return;
    }
    m_stopRequested = true;
    setState(State::Stopping);
    m_process->write("stop\n");
    m_killTimer.start(30000);
}

bool YahuServer::waitForExit(int ms)
{
    if (!m_process || m_process->state() == QProcess::NotRunning) {
        return true;
    }
    return m_process->waitForFinished(ms);
}

void YahuServer::sendCommand(const QString& command)
{
    if (!m_process || m_process->state() != QProcess::Running) {
        return;
    }
    QString cmd = command.trimmed();
    if (cmd.startsWith('/')) {
        cmd.remove(0, 1);
    }
    if (cmd.isEmpty()) {
        return;
    }
    appendLog(QStringLiteral("> ") + cmd);
    if (cmd == QLatin1String("stop")) {
        stop();
        return;
    }
    m_process->write((cmd + '\n').toUtf8());
}

void YahuServer::readOutput()
{
    if (!m_process) {
        return;
    }
    m_partialLine += QString::fromUtf8(m_process->readAll());
    QStringList lines = m_partialLine.split('\n');
    m_partialLine = lines.takeLast();

    static const QRegularExpression joined(QStringLiteral(R"(\]: (\S+) joined the game)"));
    static const QRegularExpression left(QStringLiteral(R"(\]: (\S+) left the game)"));
    for (QString line : lines) {
        line.remove('\r');
        appendLog(line);
        if (m_state == State::Starting && line.contains(QLatin1String("]: Done ("))) {
            setState(State::Running);
        }
        if (auto m = joined.match(line); m.hasMatch()) {
            m_players.insert(m.captured(1));
            emit playersChanged();
        } else if (auto m2 = left.match(line); m2.hasMatch()) {
            m_players.remove(m2.captured(1));
            emit playersChanged();
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// ServerManager

ServerManager::ServerManager(const QString& root, QObject* parent) : QObject(parent), m_root(root)
{
    QDir().mkpath(m_root);
    load();
}

ServerManager::~ServerManager()
{
    stopAll(20000);
}

void ServerManager::load()
{
    const QDir root(m_root);
    for (const QString& name : root.entryList(QDir::Dirs | QDir::NoDotAndDotDot, QDir::Name)) {
        QFile file(root.filePath(name + '/' + kConfigFile));
        if (!file.open(QIODevice::ReadOnly)) {
            continue;
        }
        const auto config = ServerConfig::fromJson(QJsonDocument::fromJson(file.readAll()).object());
        auto* server = new YahuServer(root.filePath(name), config, this);
        m_servers.append(server);
    }
}

int ServerManager::suggestPort() const
{
    int port = 25565;
    bool taken = true;
    while (taken) {
        taken = false;
        for (auto* s : m_servers) {
            if (s->config().port == port) {
                taken = true;
                ++port;
                break;
            }
        }
    }
    return port;
}

YahuServer* ServerManager::create(const ServerConfig& config, const QString& operatorName, const QString& operatorUuid)
{
    // a readable folder name, made unique
    QString slug = config.name.toLower();
    slug.replace(QRegularExpression(QStringLiteral("[^a-z0-9]+")), QStringLiteral("-"));
    slug = slug.section('-', 0, -1, QString::SectionSkipEmpty);
    if (slug.isEmpty()) {
        slug = QStringLiteral("server");
    }
    QDir root(m_root);
    QString folder = slug;
    for (int i = 2; root.exists(folder); ++i) {
        folder = slug + '-' + QString::number(i);
    }
    root.mkpath(folder);
    const QString dir = root.filePath(folder);

    // the player asked to accept the EULA in the dialog; nothing is created without it
    QFile eula(QDir(dir).filePath(QStringLiteral("eula.txt")));
    if (eula.open(QIODevice::WriteOnly | QIODevice::Text)) {
        eula.write("# Accepted in Yahu Client when the server was created (https://aka.ms/MinecraftEULA)\neula=true\n");
    }
    if (!operatorName.isEmpty() && !operatorUuid.isEmpty()) {
        QJsonObject op{ { "uuid", dashedUuid(operatorUuid) }, { "name", operatorName }, { "level", 4 }, { "bypassesPlayerLimit", true } };
        QFile ops(QDir(dir).filePath(QStringLiteral("ops.json")));
        if (ops.open(QIODevice::WriteOnly)) {
            ops.write(QJsonDocument(QJsonArray{ op }).toJson());
        }
        QJsonObject listed{ { "uuid", dashedUuid(operatorUuid) }, { "name", operatorName } };
        QFile whitelist(QDir(dir).filePath(QStringLiteral("whitelist.json")));
        if (whitelist.open(QIODevice::WriteOnly)) {
            whitelist.write(QJsonDocument(QJsonArray{ listed }).toJson());
        }
    }

    auto* server = new YahuServer(dir, config, this);
    server->setConfig(config);
    m_servers.append(server);
    emit serversChanged();
    install(server);
    return server;
}

void ServerManager::update(YahuServer* server, const ServerConfig& config)
{
    ServerConfig next = config;
    next.installedSoftware = server->config().installedSoftware;
    next.javaMajor = server->config().javaMajor;
    server->setConfig(next);
    emit serversChanged();
    if (!server->isInstalled()) {
        install(server);
    }
}

bool ServerManager::remove(YahuServer* server, QString* error)
{
    if (server->isBusy()) {
        *error = tr("Stop the server first.");
        return false;
    }
    if (!FS::trash(server->dir())) {
        *error = tr("The folder could not be moved to the recycle bin. Is a file in it still open?");
        return false;
    }
    m_servers.removeAll(server);
    server->deleteLater();
    emit serversChanged();
    return true;
}

void ServerManager::start(YahuServer* server)
{
    if (!server->isInstalled()) {
        install(server);
        return;
    }
    QString problem;
    const QString java = pickJava(server->config(), &problem);
    if (java.isEmpty()) {
        server->setFailed(problem);
        return;
    }
    server->start(java);
}

void ServerManager::stopAll(int waitMs)
{
    QElapsedTimer timer;
    timer.start();
    for (auto* server : m_servers) {
        server->stop();
    }
    for (auto* server : m_servers) {
        server->waitForExit(qMax<qint64>(500, waitMs - timer.elapsed()));
    }
}

QNetworkReply* ServerManager::get(const QString& url)
{
    QNetworkRequest request{ QUrl(url) };
    request.setHeader(QNetworkRequest::UserAgentHeader, QStringLiteral("YahuClient/0.1 (private Minecraft launcher)"));
    request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::NoLessSafeRedirectPolicy);
    return APPLICATION->network()->get(request);
}

void ServerManager::getJson(const QString& url, QObject* guard, std::function<void(const QJsonDocument&, const QString&)> done)
{
    QNetworkReply* reply = get(url);
    QPointer<QObject> alive(guard);
    connect(reply, &QNetworkReply::finished, this, [reply, alive, done] {
        reply->deleteLater();
        if (!alive) {
            return;
        }
        if (reply->error() != QNetworkReply::NoError) {
            done({}, reply->errorString());
            return;
        }
        QJsonParseError parseError;
        const auto doc = QJsonDocument::fromJson(reply->readAll(), &parseError);
        if (parseError.error != QJsonParseError::NoError) {
            done({}, parseError.errorString());
            return;
        }
        done(doc, QString());
    });
}

void ServerManager::loadVersions()
{
    if (m_loadingVersions || versionsLoaded()) {
        return;
    }
    m_loadingVersions = true;
    getJson(kManifestUrl, this, [this](const QJsonDocument& doc, const QString& error) {
        m_loadingVersions = false;
        if (!error.isEmpty()) {
            emit versionsChanged(error);
            return;
        }
        m_manifest = doc.object();
        m_versions.clear();
        for (const auto& v : m_manifest["versions"].toArray()) {
            const auto o = v.toObject();
            const QString type = o["type"].toString();
            if (type == "release" || type == "snapshot") {
                m_versions.append({ o["id"].toString(), type == "release" });
            }
        }
        emit versionsChanged(QString());
    });
}

void ServerManager::install(YahuServer* server)
{
    if (server->isBusy()) {
        return;
    }
    const ServerConfig config = server->config();
    server->setInstalling(tr("Looking up Minecraft %1…").arg(config.mcVersion));

    auto fail = [server](const QString& what) { server->setFailed(what); };

    // the last step: download the jar, then mark the server as ready
    auto download = [this, server, fail](const QString& url) {
        server->setInstalling(tr("Downloading the server…"));
        QNetworkReply* reply = get(url);
        QPointer<YahuServer> alive(server);
        connect(reply, &QNetworkReply::downloadProgress, server, [server](qint64 got, qint64 total) {
            if (total > 0) {
                server->setInstalling(tr("Downloading the server… %1%").arg(got * 100 / total));
            }
        });
        connect(reply, &QNetworkReply::finished, this, [reply, alive, fail] {
            reply->deleteLater();
            if (!alive) {
                return;
            }
            if (reply->error() != QNetworkReply::NoError) {
                fail(tr("Download failed: %1").arg(reply->errorString()));
                return;
            }
            QSaveFile jar(QDir(alive->dir()).filePath(kJar));
            if (!jar.open(QIODevice::WriteOnly) || jar.write(reply->readAll()) < 0 || !jar.commit()) {
                fail(tr("Could not save server.jar in %1").arg(alive->dir()));
                return;
            }
            ServerConfig done = alive->config();
            done.installedSoftware = done.installKey();
            alive->setConfig(done);
            alive->appendLog(tr("[Yahu] %1 %2 is installed. Press Start to run it.")
                                 .arg(ServerConfig::softwareName(done.software), done.mcVersion));
            alive->setIdle();
        });
    };

    auto withSoftware = [this, server, config, fail, download](const QJsonObject& versionJson) {
        switch (config.software) {
            case ServerConfig::Software::Vanilla: {
                const QString url = versionJson["downloads"].toObject()["server"].toObject()["url"].toString();
                if (url.isEmpty()) {
                    fail(tr("Mojang offers no server download for %1.").arg(config.mcVersion));
                    return;
                }
                download(url);
                break;
            }
            case ServerConfig::Software::Fabric: {
                server->setInstalling(tr("Finding the latest Fabric loader…"));
                getJson(QStringLiteral("https://meta.fabricmc.net/v2/versions/loader/%1").arg(config.mcVersion), server,
                        [this, server, config, fail, download](const QJsonDocument& loaders, const QString& error) {
                            const auto list = loaders.array();
                            if (!error.isEmpty() || list.isEmpty()) {
                                fail(tr("Fabric doesn't support Minecraft %1 yet.").arg(config.mcVersion));
                                return;
                            }
                            const QString loader = list.first().toObject()["loader"].toObject()["version"].toString();
                            getJson(QStringLiteral("https://meta.fabricmc.net/v2/versions/installer"), server,
                                    [config, loader, fail, download](const QJsonDocument& installers, const QString& error2) {
                                        const auto all = installers.array();
                                        if (!error2.isEmpty() || all.isEmpty()) {
                                            fail(tr("Could not reach Fabric: %1").arg(error2));
                                            return;
                                        }
                                        QString installer = all.first().toObject()["version"].toString();
                                        for (const auto& i : all) {
                                            if (i.toObject()["stable"].toBool()) {
                                                installer = i.toObject()["version"].toString();
                                                break;
                                            }
                                        }
                                        download(QStringLiteral("https://meta.fabricmc.net/v2/versions/loader/%1/%2/%3/server/jar")
                                                     .arg(config.mcVersion, loader, installer));
                                    });
                        });
                break;
            }
            case ServerConfig::Software::Paper:
            default: {
                server->setInstalling(tr("Finding the latest Paper build…"));
                getJson(QStringLiteral("https://fill.papermc.io/v3/projects/paper/versions/%1/builds/latest").arg(config.mcVersion), server,
                        [this, server, config, fail, download](const QJsonDocument& build, const QString& error) {
                            const QString url = build.object()["downloads"].toObject()["server:default"].toObject()["url"].toString();
                            if (error.isEmpty() && !url.isEmpty()) {
                                download(url);
                                return;
                            }
                            // older API as a fallback
                            const QString base = QStringLiteral("https://api.papermc.io/v2/projects/paper/versions/%1").arg(config.mcVersion);
                            getJson(base + QStringLiteral("/builds"), server,
                                    [base, config, fail, download](const QJsonDocument& builds, const QString& error2) {
                                        const auto list = builds.object()["builds"].toArray();
                                        if (!error2.isEmpty() || list.isEmpty()) {
                                            fail(tr("Paper has no build for Minecraft %1 (yet). Try Vanilla or another version.")
                                                     .arg(config.mcVersion));
                                            return;
                                        }
                                        const auto last = list.last().toObject();
                                        const QString name =
                                            last["downloads"].toObject()["application"].toObject()["name"].toString();
                                        download(QStringLiteral("%1/builds/%2/downloads/%3")
                                                     .arg(base, QString::number(last["build"].toInt()), name));
                                    });
                        });
                break;
            }
        }
    };

    // every server type needs Mojang's version info, at least to know which Java it wants
    auto withManifest = [this, server, config, fail, withSoftware]() {
        QString url;
        for (const auto& v : m_manifest["versions"].toArray()) {
            if (v.toObject()["id"].toString() == config.mcVersion) {
                url = v.toObject()["url"].toString();
            }
        }
        if (url.isEmpty()) {
            fail(tr("Minecraft %1 was not found in Mojang's version list.").arg(config.mcVersion));
            return;
        }
        getJson(url, server, [server, fail, withSoftware](const QJsonDocument& doc, const QString& error) {
            if (!error.isEmpty()) {
                fail(tr("Could not load the version details: %1").arg(error));
                return;
            }
            const auto json = doc.object();
            ServerConfig updated = server->config();
            updated.javaMajor = json["javaVersion"].toObject()["majorVersion"].toInt(21);
            server->setConfig(updated);
            withSoftware(json);
        });
    };

    if (!m_manifest.isEmpty()) {
        withManifest();
        return;
    }
    getJson(kManifestUrl, server, [this, fail, withManifest](const QJsonDocument& doc, const QString& error) {
        if (!error.isEmpty()) {
            fail(tr("Could not reach Mojang: %1").arg(error));
            return;
        }
        m_manifest = doc.object();
        withManifest();
    });
}

QList<ServerManager::JavaFound> ServerManager::javas()
{
    if (m_javasScanned) {
        return m_javas;
    }
    m_javasScanned = true;
    JavaUtils utils;
    QSet<QString> seen;
    for (QString path : utils.FindJavaPaths()) {
        // servers want a console: prefer java.exe over javaw.exe
        const QString console = QFileInfo(path).dir().filePath(QStringLiteral("java.exe"));
        if (path.endsWith(QLatin1String("javaw.exe"), Qt::CaseInsensitive) && QFileInfo::exists(console)) {
            path = console;
        }
        const QString key = QFileInfo(path).canonicalFilePath().toLower();
        if (key.isEmpty() || seen.contains(key)) {
            continue;
        }
        seen.insert(key);
        if (const int major = javaMajorAt(path); major > 0) {
            m_javas.append({ QDir::toNativeSeparators(path), major });
        }
    }
    std::sort(m_javas.begin(), m_javas.end(), [](const JavaFound& a, const JavaFound& b) { return a.major > b.major; });
    return m_javas;
}

QString ServerManager::pickJava(const ServerConfig& config, QString* problem)
{
    if (!config.javaPath.isEmpty()) {
        if (QFileInfo::exists(config.javaPath)) {
            return config.javaPath;
        }
        *problem = tr("The chosen Java (%1) doesn't exist anymore. Pick another one in the server settings.").arg(config.javaPath);
        return {};
    }
    const int need = config.javaMajor > 0 ? config.javaMajor : 21;
    // the closest version that is new enough
    const JavaFound* best = nullptr;
    for (const auto& java : javas()) {
        if (java.major >= need && (!best || java.major < best->major)) {
            best = &java;
        }
    }
    if (!best) {
        *problem = tr("This server needs Java %1 or newer, but none was found. Launch a Minecraft %2 instance once "
                      "(it downloads the right Java), or install Java %1 and try again.")
                       .arg(need)
                       .arg(config.mcVersion);
        m_javasScanned = false;  // look again next time, something may have been installed meanwhile
        m_javas.clear();
        return {};
    }
    return best->path;
}

QString ServerManager::lanAddress()
{
    // the address of this PC in the home network, ignoring virtual adapters (WSL, Hyper-V, VPNs, VMs)
    static const QStringList virtualHints = { "vethernet", "virtual", "vmware", "vbox", "wsl", "hyper-v", "tap", "tun", "loopback" };
    QString fallback;
    for (const auto& iface : QNetworkInterface::allInterfaces()) {
        const auto flags = iface.flags();
        if (!(flags & QNetworkInterface::IsUp) || !(flags & QNetworkInterface::IsRunning) || (flags & QNetworkInterface::IsLoopBack)) {
            continue;
        }
        const QString name = iface.humanReadableName().toLower();
        bool isVirtual = false;
        for (const auto& hint : virtualHints) {
            isVirtual = isVirtual || name.contains(hint);
        }
        for (const auto& entry : iface.addressEntries()) {
            const QHostAddress ip = entry.ip();
            if (ip.protocol() != QAbstractSocket::IPv4Protocol || ip.isLoopback() || ip.isLinkLocal()) {
                continue;
            }
            if (!isVirtual && ip.isPrivateUse()) {
                return ip.toString();
            }
            if (fallback.isEmpty()) {
                fallback = ip.toString();
            }
        }
    }
    return fallback.isEmpty() ? QStringLiteral("127.0.0.1") : fallback;
}

void ServerManager::fetchPublicAddress()
{
    QNetworkReply* reply = get(QStringLiteral("https://api.ipify.org"));
    connect(reply, &QNetworkReply::finished, this, [this, reply] {
        reply->deleteLater();
        if (reply->error() != QNetworkReply::NoError) {
            emit publicAddressChanged(reply->errorString());
            return;
        }
        const QString ip = QString::fromUtf8(reply->readAll()).trimmed();
        if (QHostAddress(ip).isNull()) {
            emit publicAddressChanged(tr("Unexpected answer"));
            return;
        }
        m_publicAddress = ip;
        emit publicAddressChanged(QString());
    });
}
