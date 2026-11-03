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

#include <QList>
#include <QString>

// Keeps server processes tied to the launcher.
//
// A server that outlives its launcher (the launcher crashed or was ended in the task manager) keeps running unseen:
// it holds the port and the folder, so the server can neither start again nor be deleted. New servers are put in a
// Windows job that ends them together with the launcher; servers left behind by older versions are found through the
// files they keep open and stopped.
namespace ServerProcesses {

// Ends this process when the launcher goes away, however it goes.
void tieToLauncher(qint64 pid);

// Java processes that have this file open, for example a server.jar.
QList<qint64> javaHolding(const QString& file);

// Asks the process to shut down like Ctrl+C in a console (Minecraft saves the world then), waits up to waitMs and
// ends it if it is still running. With waitMs 0 it is ended right away. True once it is gone.
bool stop(qint64 pid, int waitMs);

}  // namespace ServerProcesses
