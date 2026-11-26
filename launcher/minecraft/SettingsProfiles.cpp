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

#include "SettingsProfiles.h"

#include <QDir>
#include <QDirIterator>
#include <QFile>
#include <QFileInfo>
#include <QRegularExpression>

#include "Application.h"
#include "InstanceList.h"
#include "minecraft/MinecraftInstance.h"
#include "MMCZip.h"
#include "archive/ArchiveWriter.h"
#include "settings/SettingsObject.h"

namespace SettingsProfiles {

QStringList sharedFiles()
{
    return { QStringLiteral("options.txt"), QStringLiteral("config/yahu.json") };
}

QString root()
{
    return QDir(APPLICATION->dataRoot()).filePath(QStringLiteral("settings-profiles"));
}

QStringList names()
{
    QDir dir(root());
    QStringList list = dir.entryList(QDir::Dirs | QDir::NoDotAndDotDot, QDir::Name | QDir::IgnoreCase);
    list.removeIf([](const QString& n) { return n.endsWith(QStringLiteral(".importing")); });
    return list;
}

QString cleanName(const QString& name)
{
    QString n = name;
    n.remove(QRegularExpression(QStringLiteral(R"([\\/:*?"<>|\x00-\x1F])")));
    n = n.trimmed();
    // a folder name can't end in a dot or a space on Windows, and "." / ".." are not names
    while (n.endsWith('.') || n.endsWith(' ')) {
        n.chop(1);
    }
    return n.left(40);
}

QStringList copyShared(const QString& fromDir, const QString& toDir)
{
    QStringList copied;
    for (const QString& file : sharedFiles()) {
        const QString from = QDir(fromDir).filePath(file);
        if (!QFileInfo(from).isFile()) {
            continue;
        }
        const QString to = QDir(toDir).filePath(file);
        QDir().mkpath(QFileInfo(to).absolutePath());
        // write next to it, then swap in: a crash halfway never leaves a broken file behind
        const QString part = to + QStringLiteral(".part");
        QFile::remove(part);
        if (!QFile::copy(from, part)) {
            continue;
        }
        QFile::remove(to);
        if (QFile::rename(part, to)) {
            copied << file;
        } else {
            QFile::remove(part);
        }
    }
    return copied;
}

bool hasAny(const QString& dir)
{
    for (const QString& file : sharedFiles()) {
        if (QFileInfo(QDir(dir).filePath(file)).isFile()) {
            return true;
        }
    }
    return false;
}

bool create(const QString& name, const QString& fromGameDir)
{
    const QString n = cleanName(name);
    if (n.isEmpty()) {
        return false;
    }
    const QString dir = QDir(root()).filePath(n);
    if (QFileInfo::exists(dir)) {
        return false;
    }
    if (!QDir().mkpath(dir)) {
        return false;
    }
    if (!fromGameDir.isEmpty()) {
        copyShared(fromGameDir, dir);
    }
    return true;
}

QString exportFiles(const QString& fromDir, const QString& file)
{
    MMCZip::ArchiveWriter zip(file);
    if (auto opened = zip.open(); !opened) {
        return opened.error();
    }
    for (const QString& relative : sharedFiles()) {
        const QString path = QDir(fromDir).filePath(relative);
        if (QFileInfo(path).isFile()) {
            if (auto added = zip.addFile(path, relative); !added) {
                return added.error();
            }
        }
    }
    if (auto closed = zip.close(); !closed) {
        return closed.error();
    }
    return {};
}

QString importFiles(const QString& file, const QString& intoDir)
{
    QDir().mkpath(intoDir);
    auto extracted = MMCZip::extractDir(file, intoDir);
    if (!extracted) {
        return extracted.error();
    }
    // keep only what a profile shares, whatever else the file brought along
    const QStringList keep = sharedFiles();
    QDirIterator it(intoDir, QDir::Files, QDirIterator::Subdirectories);
    while (it.hasNext()) {
        const QString path = it.next();
        if (!keep.contains(QDir(intoDir).relativeFilePath(path))) {
            QFile::remove(path);
        }
    }
    if (!hasAny(intoDir)) {
        return QObject::tr("This file has no Minecraft or Yahu settings in it.");
    }
    return {};
}

QString exportTo(const QString& name, const QString& file)
{
    const QString n = cleanName(name);
    const QString dir = QDir(root()).filePath(n);
    if (n.isEmpty() || !QFileInfo(dir).isDir()) {
        return QObject::tr("The profile doesn't exist.");
    }
    return exportFiles(dir, file);
}

QString importFrom(const QString& file, const QString& name, QString* error)
{
    auto fail = [error](const QString& why) {
        if (error) {
            *error = why;
        }
        return QString();
    };
    const QString n = cleanName(name.isEmpty() ? QFileInfo(file).completeBaseName() : name);
    if (n.isEmpty()) {
        return fail(QObject::tr("Please give the profile a name."));
    }
    const QString target = QDir(root()).filePath(n);
    if (QFileInfo::exists(target)) {
        return fail(QObject::tr("A profile called \"%1\" already exists.").arg(n));
    }
    // read into a temporary folder first, so a broken file leaves no half profile behind
    const QString temp = target + QStringLiteral(".importing");
    QDir(temp).removeRecursively();
    const QString problem = importFiles(file, temp);
    if (!problem.isEmpty() || !QDir().rename(temp, target)) {
        QDir(temp).removeRecursively();
        return fail(problem.isEmpty() ? QObject::tr("Could not write to the profiles folder.") : problem);
    }
    return n;
}

namespace {
// every instance whose setting names one profile now names another (or none)
void repoint(const QString& from, const QString& to)
{
    auto instances = APPLICATION->instances();
    for (int i = 0; i < instances->count(); i++) {
        auto* instance = instances->at(i);
        auto settings = instance->settings();
        if (settings->contains(kSetting) && settings->get(kSetting).toString() == from) {
            settings->set(kSetting, to);
        }
    }
}
}  // namespace

bool rename(const QString& from, const QString& to)
{
    const QString n = cleanName(to);
    if (n.isEmpty() || n == from) {
        return n == from;
    }
    QDir base(root());
    if (QFileInfo::exists(base.filePath(n)) || !base.rename(from, n)) {
        return false;
    }
    repoint(from, n);
    return true;
}

bool remove(const QString& name)
{
    if (cleanName(name).isEmpty()) {
        return false;
    }
    QDir dir(QDir(root()).filePath(name));
    if (!dir.exists() || !dir.removeRecursively()) {
        return false;
    }
    repoint(name, QString());
    return true;
}

namespace {
QString profileOf(MinecraftInstance* instance)
{
    auto settings = instance->settings();
    return settings->contains(kSetting) ? settings->get(kSetting).toString() : QString();
}
}  // namespace

QString beforeStart(MinecraftInstance* instance)
{
    const QString name = profileOf(instance);
    if (name.isEmpty()) {
        return {};
    }
    const QString dir = QDir(root()).filePath(name);
    if (!hasAny(dir)) {
        // a new profile: it starts from this instance's settings
        QDir().mkpath(dir);
        copyShared(instance->gameRoot(), dir);
        return QObject::tr("Settings profile \"%1\" was empty: it starts from this instance's settings.").arg(name);
    }
    const QStringList copied = copyShared(dir, instance->gameRoot());
    return QObject::tr("Settings from profile \"%1\": %2.").arg(name, copied.join(QStringLiteral(", ")));
}

void afterGame(MinecraftInstance* instance)
{
    const QString name = profileOf(instance);
    if (name.isEmpty()) {
        return;
    }
    copyShared(instance->gameRoot(), QDir(root()).filePath(name));
}

}  // namespace SettingsProfiles
