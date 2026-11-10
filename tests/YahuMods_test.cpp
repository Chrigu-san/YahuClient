#include <QTest>

#include <minecraft/YahuMods.h>

class YahuModsTest : public QObject {
    Q_OBJECT

    const QStringList kWithApi = { "sodium-fabric-0.9.2+mc26.3.jar", "fabric-api-0.164.0+26.3.jar" };

   private slots:
    void goesIntoFitting263Instances()
    {
        QVERIFY(YahuMods::reasonToSkip("26.3", true, kWithApi).isEmpty());
        QVERIFY(YahuMods::reasonToSkip("26.3.1", true, kWithApi).isEmpty());
    }

    void needsFabric()
    {
        QVERIFY(!YahuMods::reasonToSkip("26.3", false, kWithApi).isEmpty());
    }

    void onlyForItsVersion()
    {
        QVERIFY(!YahuMods::reasonToSkip("26.2", true, kWithApi).isEmpty());
        QVERIFY(!YahuMods::reasonToSkip("26.30", true, kWithApi).isEmpty());
        QVERIFY(!YahuMods::reasonToSkip("1.21.11", true, kWithApi).isEmpty());
    }

    void needsFabricApi()
    {
        // without Fabric API the game would refuse to start, so the mod stays out
        QVERIFY(!YahuMods::reasonToSkip("26.3", true, { "sodium-fabric-0.9.2+mc26.3.jar" }).isEmpty());
        QVERIFY(!YahuMods::reasonToSkip("26.3", true, { "fabric-api-0.164.0+26.3.jar.disabled" }).isEmpty());
        QVERIFY(YahuMods::reasonToSkip("26.3", true, { "Fabric-API-0.165.2+26.3.JAR" }).isEmpty());
    }
};

QTEST_GUILESS_MAIN(YahuModsTest)

#include "YahuMods_test.moc"
