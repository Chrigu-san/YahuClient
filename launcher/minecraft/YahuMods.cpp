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

#include "YahuMods.h"

#include <QCoreApplication>
#include <QCryptographicHash>
#include <QDir>
#include <QFile>

#include "minecraft/MinecraftInstance.h"
#include "minecraft/PackProfile.h"

namespace YahuMods {

namespace {
QByteArray fileHash(const QString& path)
{
    QFile file(path);
    if (!file.open(QIODevice::ReadOnly)) {
        return {};
    }
    QCryptographicHash hash(QCryptographicHash::Sha1);
    hash.addData(&file);
    return hash.result();
}
}  // namespace

QString reasonToSkip(const QString& minecraftVersion, bool hasFabricLoader, const QStringList& modFiles)
{
    if (!hasFabricLoader) {
        return QStringLiteral("not a Fabric instance");
    }
    // 26.3 and 26.3.x, but not 26.30
    if (minecraftVersion != kMinecraftPrefix && !minecraftVersion.startsWith(kMinecraftPrefix + '.')) {
        return QStringLiteral("made for Minecraft %1, this is %2").arg(kMinecraftPrefix, minecraftVersion);
    }
    const bool hasFabricApi = std::any_of(modFiles.begin(), modFiles.end(), [](const QString& name) {
        return name.startsWith(QStringLiteral("fabric-api"), Qt::CaseInsensitive) && name.endsWith(QStringLiteral(".jar"), Qt::CaseInsensitive);
    });
    if (!hasFabricApi) {
        return QStringLiteral("Fabric API is missing");
    }
    return {};
}

QString bundledJar()
{
    return QDir(QCoreApplication::applicationDirPath()).filePath(QStringLiteral("mods/") + kJarName);
}

QString install(MinecraftInstance* instance, const QString& bundled)
{
    if (!QFile::exists(bundled)) {
        return {};
    }
    auto* profile = instance->getPackProfile();
    const QDir mods(instance->modsRoot());
    const QStringList files = mods.entryList(QDir::Files);
    const QString skip = reasonToSkip(profile->getComponentVersion(QStringLiteral("net.minecraft")),
                                      !profile->getComponentVersion(QStringLiteral("net.fabricmc.fabric-loader")).isEmpty(), files);
    if (!skip.isEmpty()) {
        return QStringLiteral("Yahu mod not added: %1.").arg(skip);
    }

    const QString target = mods.filePath(kJarName);
    if (QFile::exists(target) && fileHash(target) == fileHash(bundled)) {
        return QStringLiteral("Yahu mod is up to date.");
    }
    QDir().mkpath(mods.absolutePath());
    QFile::remove(target);
    if (!QFile::copy(bundled, target)) {
        return QStringLiteral("Yahu mod could not be copied into the instance.");
    }
    return QStringLiteral("Yahu mod added to the instance.");
}

}  // namespace YahuMods
