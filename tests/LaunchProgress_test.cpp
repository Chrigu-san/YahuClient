#include <QSignalSpy>
#include <QTest>

#include <LaunchProgress.h>

class LaunchProgressTest : public QObject {
    Q_OBJECT

   private slots:
    void estimatesTimeLeft()
    {
        // a quarter done after 5 s: the rest takes about 15 s
        QCOMPARE(LaunchProgress::secondsLeft(0.25, 5000), 15);
        QCOMPARE(LaunchProgress::secondsLeft(0.5, 10000), 10);
        // almost done still says at least a second
        QCOMPARE(LaunchProgress::secondsLeft(0.999, 10000), 1);
    }

    void noGuessTooEarly()
    {
        QCOMPARE(LaunchProgress::secondsLeft(0.02, 5000), -1);
        QCOMPARE(LaunchProgress::secondsLeft(0.5, 800), -1);
        QCOMPARE(LaunchProgress::secondsLeft(1.0, 5000), -1);
    }

    void describesTheStart()
    {
        LaunchProgress::State s;
        QVERIFY(LaunchProgress::describe(s).isEmpty());
        s.active = true;
        s.status = "Downloading libraries";
        QCOMPARE(LaunchProgress::describe(s), QString("Downloading libraries"));
        s.fraction = 0.42;
        QCOMPARE(LaunchProgress::describe(s), QString("Downloading libraries, 42%"));
        s.fraction = 0.25;
        s.elapsedMs = 5000;
        QCOMPARE(LaunchProgress::describe(s), QString("Downloading libraries, 25%, about 15 s left"));
        s.elapsedMs = 60000;
        QCOMPARE(LaunchProgress::describe(s), QString("Downloading libraries, 25%, about 3 min left"));
        s.status = "Getting the asset files from Mojang...";
        s.fraction = 1.0;
        QCOMPARE(LaunchProgress::describe(s), QString("Getting the asset files from Mojang, 100%"));
    }

    void tracksEachInstance()
    {
        auto* progress = LaunchProgress::instance();
        QSignalSpy spy(progress, &LaunchProgress::changed);
        progress->begin("a");
        QVERIFY(progress->state("a").active);
        QVERIFY(!progress->state("b").active);
        progress->update("a", "Checking assets", 0.5);
        QCOMPARE(progress->state("a").fraction, 0.5);
        progress->end("a");
        QVERIFY(!progress->state("a").active);
        QCOMPARE(spy.count(), 3);
    }
};

QTEST_GUILESS_MAIN(LaunchProgressTest)

#include "LaunchProgress_test.moc"
