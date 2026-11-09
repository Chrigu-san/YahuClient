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

#include "LaunchProgress.h"

#include <QCoreApplication>
#include <QtMath>

LaunchProgress* LaunchProgress::instance()
{
    static LaunchProgress progress;
    return &progress;
}

void LaunchProgress::begin(const QString& instanceId)
{
    Entry& e = m_entries[instanceId];
    e = Entry();
    e.active = true;
    e.status = tr("Getting ready");
    emit changed(instanceId);
}

void LaunchProgress::update(const QString& instanceId, const QString& status, double fraction)
{
    Entry& e = m_entries[instanceId];
    e.active = true;
    if (!status.isEmpty()) {
        e.status = status;
    }
    // the clock for the estimate starts with the first real progress, so time spent waiting before doesn't count
    if (fraction >= 0 && e.fraction < 0) {
        e.sinceProgress.start();
    }
    e.fraction = fraction;
    emit changed(instanceId);
}

void LaunchProgress::end(const QString& instanceId)
{
    if (m_entries.remove(instanceId) > 0) {
        emit changed(instanceId);
    }
}

LaunchProgress::State LaunchProgress::state(const QString& instanceId) const
{
    State s;
    auto it = m_entries.constFind(instanceId);
    if (it == m_entries.constEnd()) {
        return s;
    }
    s.active = it->active;
    s.status = it->status;
    s.fraction = it->fraction;
    s.elapsedMs = it->sinceProgress.isValid() ? it->sinceProgress.elapsed() : 0;
    return s;
}

int LaunchProgress::secondsLeft(double fraction, qint64 elapsedMs)
{
    // too early to say: the first moments of a download say little about the rest
    if (fraction < 0.05 || fraction >= 1.0 || elapsedMs < 1500) {
        return -1;
    }
    const double total = elapsedMs / fraction;
    return qMax(1, qCeil((total - elapsedMs) / 1000.0));
}

QString LaunchProgress::describe(const State& s)
{
    if (!s.active) {
        return {};
    }
    QString text = s.status.isEmpty() ? tr("Starting") : s.status;
    // task statuses often end in "..." or a period; the percentage follows after a comma
    while (text.endsWith('.') || text.endsWith(QChar(0x2026))) {
        text.chop(1);
    }
    if (s.fraction >= 0) {
        text += QStringLiteral(", %1%").arg(qRound(qBound(0.0, s.fraction, 1.0) * 100));
        const int left = secondsLeft(s.fraction, s.elapsedMs);
        if (left >= 60) {
            text += tr(", about %1 min left").arg((left + 30) / 60);
        } else if (left > 0) {
            text += tr(", about %1 s left").arg(left);
        }
    }
    return text;
}
