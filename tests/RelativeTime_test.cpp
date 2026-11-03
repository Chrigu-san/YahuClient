#include <QDateTime>
#include <QTest>

#include <MMCTime.h>

class RelativeTimeTest : public QObject {
    Q_OBJECT

    // a moment that lies this far in the past; a few extra seconds keep it clear of the minute boundary
    static qint64 ago(qint64 seconds) { return QDateTime::currentMSecsSinceEpoch() - (seconds + 5) * 1000; }

   private slots:
    void relativeAgo_data()
    {
        QTest::addColumn<qint64>("secondsAgo");
        QTest::addColumn<QString>("expected");

        constexpr qint64 minute = 60, hour = 3600, day = 86400;
        QTest::newRow("seconds") << qint64(10) << "just now";
        QTest::newRow("one minute") << minute << "1 minute ago";
        QTest::newRow("minutes") << 12 * minute << "12 minutes ago";
        QTest::newRow("one hour") << hour << "1 hour ago";
        QTest::newRow("hours") << 5 * hour + 20 * minute << "5 hours ago";
        QTest::newRow("one day") << day << "1 day ago";
        QTest::newRow("days and hours") << 2 * day + 5 * hour << "2 days 5 hours ago";
        QTest::newRow("one day one hour") << day + hour << "1 day 1 hour ago";
        QTest::newRow("whole days") << 3 * day << "3 days ago";
        QTest::newRow("a month or more drops hours") << 40 * day + 3 * hour << "40 days ago";
    }

    void relativeAgo()
    {
        QFETCH(qint64, secondsAgo);
        QFETCH(QString, expected);
        QCOMPARE(Time::relativeAgo(ago(secondsAgo)), expected);
    }

    void futureIsJustNow()
    {
        // a clock that was changed should never produce negative times
        QCOMPARE(Time::relativeAgo(QDateTime::currentMSecsSinceEpoch() + 3600 * 1000), QStringLiteral("just now"));
    }
};

QTEST_GUILESS_MAIN(RelativeTimeTest)

#include "RelativeTime_test.moc"
