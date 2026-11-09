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

#pragma once

#include <QString>
#include <QStringList>

class MinecraftInstance;

// Puts the Yahu mod (the in-game menu, crosshair and lyrics; see mod/) into an instance before it starts.
//
// The mod is built for one Minecraft version and needs Fabric and Fabric API, so it only goes into instances that have all
// three. The jar ships next to the launcher in mods/; an older copy in the instance is replaced, nothing else is touched.
namespace YahuMods {

// The Minecraft version the bundled mod is made for.
inline const QString kMinecraftPrefix = QStringLiteral("26.3");
inline const QString kJarName = QStringLiteral("yahu-mod.jar");

// Why the mod can't go into an instance with these traits, or an empty string if it can. Kept separate for the tests.
QString reasonToSkip(const QString& minecraftVersion, bool hasFabricLoader, const QStringList& modFiles);

// Copies the bundled jar into the instance if it fits there. Returns what happened, for the instance log.
QString install(MinecraftInstance* instance, const QString& bundledJar);

// Where the launcher keeps the bundled jar.
QString bundledJar();

}  // namespace YahuMods
