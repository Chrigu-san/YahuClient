#include <QTest>

#include <servers/LocalHosts.h>

class LocalHostsTest : public QObject {
    Q_OBJECT

    // a typical Windows hosts file, with comments, a user entry and CRLF line ends
    const QString kStock = QStringLiteral(
        "# Copyright (c) 1993-2009 Microsoft Corp.\r\n"
        "#\r\n"
        "#      127.0.0.1       localhost\r\n"
        "192.168.1.50 nas.home\r\n");

   private slots:
    void findsOurLines()
    {
        const QString text = kStock + QStringLiteral("127.0.0.1 chris.yahuclient.net # Yahu Client\r\n"
                                                     "127.0.0.1\tfreunde.yahuclient.net   #Yahu Client  \r\n"
                                                     "127.0.0.1 other.example # someone else\r\n");
        QCOMPARE(LocalHosts::pointedNames(text), QStringList({ "chris.yahuclient.net", "freunde.yahuclient.net" }));
        QVERIFY(LocalHosts::pointedNames(kStock).isEmpty());
    }

    void addsLinesAndKeepsTheRest()
    {
        const QString out = LocalHosts::rewritten(kStock, { "chris.yahuclient.net" });
        QVERIFY(out.startsWith(kStock));
        QVERIFY(out.endsWith("127.0.0.1 chris.yahuclient.net # Yahu Client\r\n"));
        QCOMPARE(LocalHosts::pointedNames(out), QStringList({ "chris.yahuclient.net" }));
    }

    void replacesOldLines()
    {
        // a renamed server: the old name goes, the new one comes, user lines stay
        const QString before = LocalHosts::rewritten(kStock, { "old.yahuclient.net", "keep.yahuclient.net" });
        const QString after = LocalHosts::rewritten(before, { "new.yahuclient.net", "keep.yahuclient.net" });
        QCOMPARE(LocalHosts::pointedNames(after), QStringList({ "new.yahuclient.net", "keep.yahuclient.net" }));
        QVERIFY(after.contains("192.168.1.50 nas.home"));
        QCOMPARE(after.count("# Yahu Client"), 2);
    }

    void removesAllWhenEmpty()
    {
        const QString with = LocalHosts::rewritten(kStock, { "chris.yahuclient.net" });
        const QString without = LocalHosts::rewritten(with, {});
        QVERIFY(LocalHosts::pointedNames(without).isEmpty());
        QVERIFY(without.contains("nas.home"));
    }

    void dropsUnsafeNames()
    {
        // these end up in a script that runs as admin; anything but a host name is left out
        const QString out = LocalHosts::rewritten(kStock, { "evil.example\r\n0.0.0.0 microsoft.com", "a b", "chris.yahuclient.net" });
        QCOMPARE(LocalHosts::pointedNames(out), QStringList({ "chris.yahuclient.net" }));
        QVERIFY(!out.contains("microsoft"));
    }

    void stableForEmptyFile()
    {
        QCOMPARE(LocalHosts::rewritten(QString(), { "chris.yahuclient.net" }), QStringLiteral("127.0.0.1 chris.yahuclient.net # Yahu Client\r\n"));
    }
};

QTEST_GUILESS_MAIN(LocalHostsTest)

#include "LocalHosts_test.moc"
