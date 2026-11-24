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

// Settings profiles: named sets of game settings (PvP, Building, ...) shared by every instance that picks one.
//
// A profile is a folder in the launcher's data folder (settings-profiles/<name>) holding Minecraft's options.txt (every
// setting and key binding) and the Yahu mod's config/yahu.json (crosshair, lyrics, sky, inventory, menu). Before an
// instance with a profile starts, the profile is copied into it; when the game ends, the instance's files are copied back,
// so what you change in the game reaches every instance on that profile. A new, empty profile starts from the settings of
// the first instance that uses it. Instances without a profile keep their own settings, untouched.
namespace SettingsProfiles {

// The instance setting that names its profile (empty: its own settings).
inline const QString kSetting = QStringLiteral("YahuSettingsProfile");

// The files a profile shares, relative to the game folder.
QStringList sharedFiles();

// The folder holding all profiles.
QString root();

// The profiles there, sorted.
QStringList names();

// A name usable as a folder: trimmed, without characters a file name can't have; empty if nothing is left.
QString cleanName(const QString& name);

// Makes a profile; with a game folder, it starts from that folder's settings. False if the name is taken or unusable.
bool create(const QString& name, const QString& fromGameDir = {});

// Renames a profile (and every instance that uses it). False if the new name is taken or unusable.
bool rename(const QString& from, const QString& to);

// Deletes a profile; instances that used it go back to their own settings.
bool remove(const QString& name);

// Copies the shared files that exist from one folder to another (making folders as needed). Returns the ones copied.
QStringList copyShared(const QString& fromDir, const QString& toDir);

// Whether a folder has any of the shared files.
bool hasAny(const QString& dir);

// Before the game starts: the profile into the instance (or, for a new profile, the instance into the profile).
// Returns what happened, for the instance log (empty without a profile).
QString beforeStart(MinecraftInstance* instance);

// After the game ended: the instance's settings back into its profile.
void afterGame(MinecraftInstance* instance);

}  // namespace SettingsProfiles
