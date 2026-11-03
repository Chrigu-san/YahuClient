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

#include "ServerProcesses.h"

#ifdef Q_OS_WIN
#include <QDir>
#include <QFileInfo>

#include <windows.h>

#include <restartmanager.h>

#include <iterator>
#include <string>
#include <vector>
#endif

namespace ServerProcesses {

#ifdef Q_OS_WIN

namespace {

// One job for all servers. The launcher holds the only handle, so Windows closes it, and ends the servers in it,
// when the launcher process ends. A normal quit stops and saves the servers before that.
HANDLE serverJob()
{
    static HANDLE job = [] {
        HANDLE h = CreateJobObjectW(nullptr, nullptr);
        if (h) {
            JOBOBJECT_EXTENDED_LIMIT_INFORMATION info{};
            info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
            SetInformationJobObject(h, JobObjectExtendedLimitInformation, &info, sizeof(info));
        }
        return h;
    }();
    return job;
}

bool isJava(DWORD pid)
{
    HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
    if (!process) {
        return false;
    }
    wchar_t path[MAX_PATH * 2];
    DWORD size = DWORD(std::size(path));
    const bool ok = QueryFullProcessImageNameW(process, 0, path, &size);
    CloseHandle(process);
    if (!ok) {
        return false;
    }
    const QString name = QFileInfo(QString::fromWCharArray(path, int(size))).fileName().toLower();
    return name == QStringLiteral("java.exe") || name == QStringLiteral("javaw.exe");
}

// Sends Ctrl+C to the console the process runs in. The launcher has no console of its own (when it has one, from
// being started in a terminal, this is skipped so that console isn't lost).
bool sendCtrlC(DWORD pid)
{
    if (GetConsoleWindow() || !AttachConsole(pid)) {
        return false;
    }
    SetConsoleCtrlHandler(nullptr, TRUE);  // the event goes to everyone on that console, us included
    const bool sent = GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0);
    Sleep(200);
    FreeConsole();
    SetConsoleCtrlHandler(nullptr, FALSE);
    return sent;
}

}  // namespace

void tieToLauncher(qint64 pid)
{
    HANDLE job = serverJob();
    HANDLE process = OpenProcess(PROCESS_SET_QUOTA | PROCESS_TERMINATE, FALSE, DWORD(pid));
    if (job && process) {
        AssignProcessToJobObject(job, process);
    }
    if (process) {
        CloseHandle(process);
    }
}

QList<qint64> javaHolding(const QString& file)
{
    QList<qint64> found;
    DWORD session = 0;
    wchar_t key[CCH_RM_SESSION_KEY + 1] = {};
    if (RmStartSession(&session, 0, key) != ERROR_SUCCESS) {
        return found;
    }
    const std::wstring path = QDir::toNativeSeparators(QFileInfo(file).absoluteFilePath()).toStdWString();
    LPCWSTR files[] = { path.c_str() };
    if (RmRegisterResources(session, 1, files, 0, nullptr, 0, nullptr) == ERROR_SUCCESS) {
        UINT needed = 0;
        UINT count = 0;
        DWORD reasons = 0;
        DWORD result = RmGetList(session, &needed, &count, nullptr, &reasons);
        if (result == ERROR_MORE_DATA && needed > 0) {
            std::vector<RM_PROCESS_INFO> infos(needed);
            count = needed;
            result = RmGetList(session, &needed, &count, infos.data(), &reasons);
            if (result == ERROR_SUCCESS) {
                for (UINT i = 0; i < count; i++) {
                    const DWORD pid = infos[i].Process.dwProcessId;
                    if (pid != GetCurrentProcessId() && isJava(pid)) {
                        found << pid;
                    }
                }
            }
        }
    }
    RmEndSession(session);
    return found;
}

bool stop(qint64 pid, int waitMs)
{
    HANDLE process = OpenProcess(SYNCHRONIZE | PROCESS_TERMINATE, FALSE, DWORD(pid));
    if (!process) {
        return true;  // already gone
    }
    bool gone = false;
    if (waitMs > 0 && sendCtrlC(DWORD(pid))) {
        gone = WaitForSingleObject(process, DWORD(waitMs)) == WAIT_OBJECT_0;
    }
    if (!gone) {
        TerminateProcess(process, 1);
        gone = WaitForSingleObject(process, 5000) == WAIT_OBJECT_0;
    }
    CloseHandle(process);
    return gone;
}

#else

// Elsewhere the servers are children of the launcher and nothing like the Windows job is needed for now.
void tieToLauncher(qint64) {}

QList<qint64> javaHolding(const QString&)
{
    return {};
}

bool stop(qint64, int)
{
    return true;
}

#endif

}  // namespace ServerProcesses
