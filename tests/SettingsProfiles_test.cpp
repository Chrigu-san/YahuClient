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

#include <QDir>
#include <QFile>
#include <QTemporaryDir>
#include <QTest>

#include "minecraft/SettingsProfiles.h"

class SettingsProfilesTest : public QObject {
    Q_OBJECT

    static void write(const QString& path, const QByteArray& data)
    {
        QDir().mkpath(QFileInfo(path).absolutePath());
        QFile f(path);
        QVERIFY(f.open(QIODevice::WriteOnly));
        f.write(data);
    }

    static QByteArray read(const QString& path)
    {
        QFile f(path);
        return f.open(QIODevice::ReadOnly) ? f.readAll() : QByteArray();
    }

   private slots:
    void namesAreCleanedForFolders()
    {
        QCOMPARE(SettingsProfiles::cleanName("  PvP  "), QString("PvP"));
        QCOMPARE(SettingsProfiles::cleanName("Build/ing:*?"), QString("Building"));
        QCOMPARE(SettingsProfiles::cleanName("trailing dots..."), QString("trailing dots"));
        QCOMPARE(SettingsProfiles::cleanName("..."), QString());
        QCOMPARE(SettingsProfiles::cleanName("   "), QString());
        QVERIFY(SettingsProfiles::cleanName(QString(80, 'a')).size() <= 40);
    }

    void copiesOnlyTheSharedFiles()
    {
        QTemporaryDir game, profile;
        write(game.filePath("options.txt"), "fov:0.5\nkey_key.jump:key.keyboard.space\n");
        write(game.filePath("config/yahu.json"), "{\"crosshair\":{}}");
        write(game.filePath("config/other-mod.json"), "{}");
        write(game.filePath("servers.dat"), "x");

        const QStringList copied = SettingsProfiles::copyShared(game.path(), profile.path());
        QCOMPARE(copied, QStringList({"options.txt", "config/yahu.json"}));
        QCOMPARE(read(profile.filePath("options.txt")), QByteArray("fov:0.5\nkey_key.jump:key.keyboard.space\n"));
        QCOMPARE(read(profile.filePath("config/yahu.json")), QByteArray("{\"crosshair\":{}}"));
        QVERIFY(!QFile::exists(profile.filePath("config/other-mod.json")));
        QVERIFY(!QFile::exists(profile.filePath("servers.dat")));
    }

    void overwritesAndLeavesNoPartFiles()
    {
        QTemporaryDir from, to;
        write(from.filePath("options.txt"), "new");
        write(to.filePath("options.txt"), "old");
        SettingsProfiles::copyShared(from.path(), to.path());
        QCOMPARE(read(to.filePath("options.txt")), QByteArray("new"));
        QVERIFY(!QFile::exists(to.filePath("options.txt.part")));
    }

    void missingFilesAreSkipped()
    {
        QTemporaryDir from, to;
        write(from.filePath("config/yahu.json"), "{}");
        QVERIFY(SettingsProfiles::hasAny(from.path()));
        QVERIFY(!SettingsProfiles::hasAny(to.path()));
        QCOMPARE(SettingsProfiles::copyShared(from.path(), to.path()), QStringList({"config/yahu.json"}));
        QVERIFY(!QFile::exists(to.filePath("options.txt")));
    }

    void exportsAndImportsAProfileFile()
    {
        QTemporaryDir game, out, back;
        write(game.filePath("options.txt"), "fov:0.7\n");
        write(game.filePath("config/yahu.json"), "{\"lyrics\":{}}");
        write(game.filePath("config/other-mod.json"), "{}");
        const QString file = out.filePath("PvP.yahuprofile");

        QCOMPARE(SettingsProfiles::exportFiles(game.path(), file), QString());
        QVERIFY(QFileInfo(file).size() > 0);
        QCOMPARE(SettingsProfiles::importFiles(file, back.filePath("PvP")), QString());
        QCOMPARE(read(back.filePath("PvP/options.txt")), QByteArray("fov:0.7\n"));
        QCOMPARE(read(back.filePath("PvP/config/yahu.json")), QByteArray("{\"lyrics\":{}}"));
        QVERIFY(!QFile::exists(back.filePath("PvP/config/other-mod.json")));
    }

    void importRejectsFilesWithoutSettings()
    {
        QTemporaryDir game, out, back;
        write(game.filePath("servers.dat"), "x");
        const QString file = out.filePath("nothing.yahuprofile");
        QCOMPARE(SettingsProfiles::exportFiles(game.path(), file), QString());
        QVERIFY(!SettingsProfiles::importFiles(file, back.filePath("x")).isEmpty());
        QVERIFY(!SettingsProfiles::importFiles(out.filePath("missing.yahuprofile"), back.filePath("y")).isEmpty());
    }
};

QTEST_GUILESS_MAIN(SettingsProfilesTest)

#include "SettingsProfiles_test.moc"
