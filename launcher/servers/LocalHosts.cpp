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

#include "LocalHosts.h"

#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QRegularExpression>

#ifdef Q_OS_WIN
#include <windows.h>
#include <shellapi.h>
#endif

namespace LocalHosts {

namespace {
// a Yahu line: "127.0.0.1 <name> # Yahu Client" (spacing may vary)
const QRegularExpression& yahuLine()
{
    static const QRegularExpression re(QStringLiteral("^\\s*127\\.0\\.0\\.1\\s+(\\S+)\\s*#\\s*Yahu Client\\s*$"));
    return re;
}

bool isYahuLine(const QString& line)
{
    static const QRegularExpression marker(QStringLiteral("#\\s*Yahu Client\\s*$"));
    return marker.match(line).hasMatch();
}

// host names only: they end up in a script that runs with admin rights
bool safeName(const QString& name)
{
    static const QRegularExpression re(QStringLiteral("^[a-z0-9]([a-z0-9.-]{0,251}[a-z0-9])?$"));
    return re.match(name).hasMatch();
}

QString hostsPath()
{
    const QString root = qEnvironmentVariable("SystemRoot", QStringLiteral("C:\\Windows"));
    return QDir(root).filePath(QStringLiteral("System32/drivers/etc/hosts"));
}
}  // namespace

QStringList pointedNames(const QString& hostsText)
{
    QStringList names;
    for (const QString& line : hostsText.split('\n')) {
        const auto m = yahuLine().match(QString(line).remove('\r'));
        if (m.hasMatch()) {
            names << m.captured(1).toLower();
        }
    }
    return names;
}

QString rewritten(const QString& hostsText, const QStringList& names)
{
    QStringList lines;
    for (const QString& line : hostsText.split('\n')) {
        if (!isYahuLine(QString(line).remove('\r'))) {
            lines << QString(line).remove('\r');
        }
    }
    while (!lines.isEmpty() && lines.last().trimmed().isEmpty()) {
        lines.removeLast();
    }
    for (const QString& name : names) {
        if (safeName(name)) {
            lines << QStringLiteral("127.0.0.1 %1 %2").arg(name, kMarker);
        }
    }
    return lines.join(QStringLiteral("\r\n")) + QStringLiteral("\r\n");
}

QString read()
{
    QFile file(hostsPath());
    if (!file.open(QIODevice::ReadOnly)) {
        return {};
    }
    return QString::fromLocal8Bit(file.readAll());
}

bool covers(const QStringList& names)
{
    const QStringList pointed = pointedNames(read());
    for (const QString& name : names) {
        if (!pointed.contains(name.toLower())) {
            return false;
        }
    }
    return true;
}

bool apply(const QStringList& names, QString* error)
{
#ifdef Q_OS_WIN
    // The hosts file needs admin rights. A tiny PowerShell script does the edit with the UAC prompt; it keeps every
    // line that isn't ours and adds one line per name, the same way rewritten() does.
    QString script = QStringLiteral(
        "$ErrorActionPreference = 'Stop'\n"
        "try {\n"
        "  $h = Join-Path $env:SystemRoot 'System32\\drivers\\etc\\hosts'\n"
        "  $keep = @()\n"
        "  if (Test-Path -LiteralPath $h) { $keep = @(Get-Content -LiteralPath $h | Where-Object { $_ -notmatch '#\\s*Yahu Client\\s*$' }) }\n");
    for (const QString& name : names) {
        if (!safeName(name)) {
            *error = QCoreApplication::translate("LocalHosts", "\"%1\" isn't a valid address.").arg(name);
            return false;
        }
        script += QStringLiteral("  $keep += '127.0.0.1 %1 %2'\n").arg(name, kMarker);
    }
    script += QStringLiteral(
        "  [IO.File]::WriteAllLines($h, [string[]]$keep)\n"
        "  ipconfig /flushdns | Out-Null\n"
        "  exit 0\n"
        "} catch { exit 1 }\n");

    // -EncodedCommand takes UTF-16LE in base64, which avoids every quoting problem
    QByteArray utf16(reinterpret_cast<const char*>(script.utf16()), script.size() * 2);
    const QString params = QStringLiteral("-NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -EncodedCommand %1")
                               .arg(QString::fromLatin1(utf16.toBase64()));
    const std::wstring file = L"powershell.exe";
    const std::wstring args = params.toStdWString();

    SHELLEXECUTEINFOW info{};
    info.cbSize = sizeof(info);
    info.fMask = SEE_MASK_NOCLOSEPROCESS | SEE_MASK_NO_CONSOLE;
    info.lpVerb = L"runas";
    info.lpFile = file.c_str();
    info.lpParameters = args.c_str();
    info.nShow = SW_HIDE;
    if (!ShellExecuteExW(&info) || !info.hProcess) {
        *error = GetLastError() == ERROR_CANCELLED
                     ? QCoreApplication::translate("LocalHosts", "Windows didn't get the permission.")
                     : QCoreApplication::translate("LocalHosts", "Windows couldn't start the change.");
        return false;
    }
    WaitForSingleObject(info.hProcess, 30000);
    DWORD code = 1;
    GetExitCodeProcess(info.hProcess, &code);
    CloseHandle(info.hProcess);
    if (code != 0 || !covers(names)) {
        *error = QCoreApplication::translate("LocalHosts", "The hosts file couldn't be changed.");
        return false;
    }
    return true;
#else
    Q_UNUSED(names)
    *error = QCoreApplication::translate("LocalHosts", "Only supported on Windows.");
    return false;
#endif
}

}  // namespace LocalHosts
