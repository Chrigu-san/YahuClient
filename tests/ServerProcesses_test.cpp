#include <QCoreApplication>
#include <QFile>
#include <QProcess>
#include <QStandardPaths>
#include <QTemporaryDir>
#include <QTest>

#include <servers/ServerProcesses.h>

#ifdef Q_OS_WIN
#include <windows.h>
#endif

// Needs a java on the PATH; it plays a server left behind by an older launcher: it keeps a file open like server.jar
// and, like Minecraft, does its saving in a shutdown hook.
class ServerProcessesTest : public QObject {
    Q_OBJECT

    const QString kProgram = QStringLiteral(
        "public class Hold {\n"
        "    public static void main(String[] a) throws Exception {\n"
        "        var in = new java.io.FileInputStream(a[0]);\n"
        "        Runtime.getRuntime().addShutdownHook(new Thread(() -> {\n"
        "            try { java.nio.file.Files.writeString(java.nio.file.Path.of(a[1]), \"saved\"); } catch (Exception e) {}\n"
        "        }));\n"
        "        System.out.println(\"ready\");\n"
        "        Thread.sleep(120000);\n"
        "    }\n"
        "}\n");

    QTemporaryDir m_dir;
    QString m_java;

    QString path(const QString& name) const { return m_dir.filePath(name); }

    // Starts the stand-in server like the launcher starts a real one and waits until it holds the jar.
    bool startHolder(QProcess& process)
    {
        process.setProgram(m_java);
        process.setArguments({ path("Hold.java"), path("server.jar"), path("saved.txt") });
        process.start();
        return process.waitForStarted(10000) && process.waitForReadyRead(30000);
    }

   private slots:
    void initTestCase()
    {
#ifndef Q_OS_WIN
        QSKIP("Windows only");
#endif
        m_java = QStandardPaths::findExecutable("java");
        if (m_java.isEmpty()) {
            QSKIP("no java on the PATH");
        }
#ifdef Q_OS_WIN
        // the launcher has no console; when the tests run in one, Ctrl+C can't be sent and only the hard stop is tested
        FreeConsole();
#endif
        QVERIFY(m_dir.isValid());
        QFile program(path("Hold.java"));
        QVERIFY(program.open(QIODevice::WriteOnly));
        program.write(kProgram.toUtf8());
        program.close();
        QFile jar(path("server.jar"));
        QVERIFY(jar.open(QIODevice::WriteOnly));
        jar.write("not really a jar");
    }

    void findsAndSavesALeftover()
    {
        QFile::remove(path("saved.txt"));
        QProcess holder;
        QVERIFY(startHolder(holder));

        const auto found = ServerProcesses::javaHolding(path("server.jar"));
        QCOMPARE(found, QList<qint64>({ holder.processId() }));

        QVERIFY(ServerProcesses::stop(holder.processId(), 15000));
        QVERIFY(holder.waitForFinished(5000));
        QVERIFY2(QFile::exists(path("saved.txt")), "the shutdown hook did not run, so a world would not have been saved");
        QVERIFY(ServerProcesses::javaHolding(path("server.jar")).isEmpty());
    }

    void endsRightAway()
    {
        QFile::remove(path("saved.txt"));
        QProcess holder;
        QVERIFY(startHolder(holder));
        QVERIFY(ServerProcesses::stop(holder.processId(), 0));
        QVERIFY(holder.waitForFinished(5000));
        QVERIFY(ServerProcesses::javaHolding(path("server.jar")).isEmpty());
    }

    void ignoresFilesNobodyHolds() { QVERIFY(ServerProcesses::javaHolding(path("Hold.java")).isEmpty()); }

    void tiedServerEndsWithLauncher()
    {
        // this test binary, run again with only tieHelper, plays the launcher; it is then ended the hard way
        QProcess launcher;
        auto env = QProcessEnvironment::systemEnvironment();
        env.insert("YAHU_TIE_HELPER", m_dir.path());
        launcher.setProcessEnvironment(env);
        launcher.start(QCoreApplication::applicationFilePath(), { "ServerProcesses_test", "tieHelper" });
        QVERIFY(launcher.waitForStarted(10000));
        QTRY_VERIFY_WITH_TIMEOUT(!ServerProcesses::javaHolding(path("server.jar")).isEmpty(), 30000);
        const auto servers = ServerProcesses::javaHolding(path("server.jar"));

        launcher.kill();
        QVERIFY(launcher.waitForFinished(5000));
        QTRY_VERIFY_WITH_TIMEOUT(ServerProcesses::javaHolding(path("server.jar")).isEmpty(), 10000);
        for (const qint64 pid : servers) {
            ServerProcesses::stop(pid, 0);  // only matters when the test failed
        }
    }

    void tieHelper()
    {
        const QString dir = qEnvironmentVariable("YAHU_TIE_HELPER");
        if (dir.isEmpty()) {
            QSKIP("only runs as the stand-in launcher of tiedServerEndsWithLauncher");
        }
        auto* server = new QProcess;
        server->start(m_java, { dir + "/Hold.java", dir + "/server.jar", dir + "/tied.txt" });
        QVERIFY(server->waitForStarted(10000));
        ServerProcesses::tieToLauncher(server->processId());
        QTest::qWait(60000);  // ended by the test long before
    }
};

QTEST_GUILESS_MAIN(ServerProcessesTest)

#include "ServerProcesses_test.moc"
