#include <QDir>
#include <QFile>
#include <QTemporaryDir>
#include <QTest>

#include <servers/ServerManager.h>

class YahuServerTest : public QObject {
    Q_OBJECT

    static QString readFile(const QString& path)
    {
        QFile file(path);
        if (!file.open(QIODevice::ReadOnly)) {
            return {};
        }
        return QString::fromUtf8(file.readAll());
    }

    static ServerConfig sample()
    {
        ServerConfig c;
        c.name = QStringLiteral("Freunde Survival");
        c.software = ServerConfig::Software::Fabric;
        c.mcVersion = QStringLiteral("1.21.11");
        c.memoryMb = 6144;
        c.port = 25570;
        c.motd = QStringLiteral("Hallo");
        c.seed = QStringLiteral("-123456789");
        c.gamemode = QStringLiteral("creative");
        c.difficulty = QStringLiteral("hard");
        c.hardcore = true;
        c.maxPlayers = 4;
        c.onlineMode = false;
        c.whitelist = true;
        c.pvp = false;
        c.viewDistance = 16;
        c.idleTimeout = 15;
        c.jvmArgs = QStringLiteral("-XX:+UseG1GC");
        c.javaMajor = 21;
        c.installedSoftware = QStringLiteral("fabric 1.21.11");
        return c;
    }

   private slots:
    void configSurvivesJson()
    {
        const ServerConfig c = sample();
        const ServerConfig back = ServerConfig::fromJson(c.toJson());
        QCOMPARE(back.name, c.name);
        QCOMPARE(back.software, c.software);
        QCOMPARE(back.mcVersion, c.mcVersion);
        QCOMPARE(back.memoryMb, c.memoryMb);
        QCOMPARE(back.port, c.port);
        QCOMPARE(back.seed, c.seed);
        QCOMPARE(back.gamemode, c.gamemode);
        QCOMPARE(back.difficulty, c.difficulty);
        QCOMPARE(back.hardcore, c.hardcore);
        QCOMPARE(back.maxPlayers, c.maxPlayers);
        QCOMPARE(back.onlineMode, c.onlineMode);
        QCOMPARE(back.whitelist, c.whitelist);
        QCOMPARE(back.pvp, c.pvp);
        QCOMPARE(back.viewDistance, c.viewDistance);
        QCOMPARE(back.idleTimeout, c.idleTimeout);
        QCOMPARE(back.jvmArgs, c.jvmArgs);
        QCOMPARE(back.javaMajor, c.javaMajor);
        QCOMPARE(back.installedSoftware, c.installedSoftware);
    }

    void emptyJsonGivesDefaults()
    {
        const ServerConfig c = ServerConfig::fromJson({});
        const ServerConfig defaults;
        QCOMPARE(c.software, ServerConfig::Software::Paper);
        QCOMPARE(c.port, defaults.port);
        QCOMPARE(c.memoryMb, defaults.memoryMb);
        QCOMPARE(c.maxPlayers, defaults.maxPlayers);
        QCOMPARE(c.onlineMode, true);
    }

    void installKeyFollowsSoftwareAndVersion()
    {
        ServerConfig a = sample();
        ServerConfig b = a;
        QCOMPARE(a.installKey(), b.installKey());
        b.memoryMb = 2048;  // settings that don't need a new server.jar
        b.motd = QStringLiteral("other");
        QCOMPARE(a.installKey(), b.installKey());
        b.mcVersion = QStringLiteral("1.21.10");
        QVERIFY(a.installKey() != b.installKey());
        b = a;
        b.software = ServerConfig::Software::Paper;
        QVERIFY(a.installKey() != b.installKey());
    }

    void softwareIdsAreStable()
    {
        // these end up in yahu-server.json on disk, so they must never change
        QCOMPARE(ServerConfig::softwareId(ServerConfig::Software::Paper), QStringLiteral("paper"));
        QCOMPARE(ServerConfig::softwareId(ServerConfig::Software::Vanilla), QStringLiteral("vanilla"));
        QCOMPARE(ServerConfig::softwareId(ServerConfig::Software::Fabric), QStringLiteral("fabric"));
    }

    void writesProperties()
    {
        QTemporaryDir dir;
        QVERIFY(dir.isValid());
        YahuServer server(dir.path(), sample());
        server.setConfig(sample());

        const QString props = readFile(QDir(dir.path()).filePath("server.properties"));
        QVERIFY(props.contains("server-port=25570\n"));
        QVERIFY(props.contains("max-players=4\n"));
        QVERIFY(props.contains("online-mode=false\n"));
        QVERIFY(props.contains("white-list=true\n"));
        QVERIFY(props.contains("enforce-whitelist=true\n"));
        QVERIFY(props.contains("gamemode=creative\n"));
        QVERIFY(props.contains("hardcore=true\n"));
        QVERIFY(props.contains("level-seed=-123456789\n"));
        QVERIFY(QFile::exists(QDir(dir.path()).filePath("yahu-server.json")));
    }

    void keepsForeignKeysAndEscapes()
    {
        QTemporaryDir dir;
        QVERIFY(dir.isValid());
        {
            // what the server itself wrote on first start, with a key Yahu Client doesn't manage
            QFile file(QDir(dir.path()).filePath("server.properties"));
            QVERIFY(file.open(QIODevice::WriteOnly));
            file.write("#Minecraft server properties\nrcon.port=25575\nmotd=old\nserver-port=1\n");
        }
        ServerConfig c = sample();
        c.motd = QStringLiteral("Grüezi\nzäme \\o/");
        YahuServer server(dir.path(), c);
        server.writeProperties();

        const QString props = readFile(QDir(dir.path()).filePath("server.properties"));
        QVERIFY(props.startsWith("#Minecraft server properties\n"));
        QVERIFY(props.contains("rcon.port=25575\n"));
        QVERIFY(props.contains("server-port=25570\n"));
        QVERIFY(!props.contains("server-port=1\n"));
        QVERIFY(!props.contains("motd=old"));
        // Java .properties: non-ASCII as \uXXXX, line breaks and backslashes escaped
        QVERIFY2(props.contains("motd=Gr\\u00fcezi\\nz\\u00e4me \\\\o/\n"), qPrintable(props));
        QCOMPARE(props.count("server-port="), 1);
    }

    void startsStopped()
    {
        QTemporaryDir dir;
        YahuServer server(dir.path(), sample());
        QCOMPARE(server.state(), YahuServer::State::Stopped);
        QVERIFY(!server.isBusy());
        QVERIFY(!server.isInstalled());  // no server.jar yet
    }

    void installedOnlyWithMatchingJar()
    {
        QTemporaryDir dir;
        ServerConfig c = sample();
        c.installedSoftware = c.installKey();
        YahuServer server(dir.path(), c);
        QVERIFY(!server.isInstalled());
        QFile jar(QDir(dir.path()).filePath("server.jar"));
        QVERIFY(jar.open(QIODevice::WriteOnly));
        jar.close();
        QVERIFY(server.isInstalled());

        c.mcVersion = QStringLiteral("1.20.1");  // a different version needs a new download
        YahuServer other(dir.path(), c);
        QVERIFY(!other.isInstalled());
    }

    void failureIsLogged()
    {
        QTemporaryDir dir;
        YahuServer server(dir.path(), sample());
        server.setFailed(QStringLiteral("Java not found"));
        QCOMPARE(server.state(), YahuServer::State::Failed);
        QCOMPARE(server.detail(), QStringLiteral("Java not found"));
        QVERIFY(server.log().last().contains("Java not found"));
        server.setIdle();
        QCOMPARE(server.state(), YahuServer::State::Stopped);
    }
};

QTEST_GUILESS_MAIN(YahuServerTest)

#include "YahuServer_test.moc"
