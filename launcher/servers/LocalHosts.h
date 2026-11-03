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

// Lets the player join their own server with its name (chris.yahuclient.net) from this PC.
//
// Most home routers can't reach their own public address from inside ("NAT loopback"), so on the PC that hosts the
// server the name has to lead to 127.0.0.1 instead. That is done with a line in the Windows hosts file, written once
// with the player's permission (UAC). Minecraft still finds the port through the SRV record in DNS.
namespace LocalHosts {

// Marks the lines Yahu Client owns in the hosts file.
inline const QString kMarker = QStringLiteral("# Yahu Client");

// Host names the hosts file text already points at 127.0.0.1 through a Yahu line.
QStringList pointedNames(const QString& hostsText);

// The hosts file text with all Yahu lines replaced by one line per name; everything else stays as it was.
QString rewritten(const QString& hostsText, const QStringList& names);

// The current hosts file of this PC, or empty if it can't be read.
QString read();

// True if every name already leads to this PC.
bool covers(const QStringList& names);

// Asks Windows for permission once and writes the Yahu lines for these names. Blocks until it is done.
bool apply(const QStringList& names, QString* error);

}  // namespace LocalHosts
