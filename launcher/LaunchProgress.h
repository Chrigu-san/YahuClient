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

#include <QElapsedTimer>
#include <QHash>
#include <QObject>
#include <QString>

// How far the start of each instance is, for the banner under the Play button. There is no progress window any more: the
// launch reports here and the banner shows one quiet line ("Starting... 42%, about 12 s left") and a thin bar.
class LaunchProgress : public QObject {
    Q_OBJECT
   public:
    struct State {
        bool active = false;
        QString status;          // what the launch does right now, e.g. "Downloading libraries"
        double fraction = -1.0;  // 0..1, or below 0 while it isn't known
        qint64 elapsedMs = 0;    // since the progress started counting
    };

    static LaunchProgress* instance();

    void begin(const QString& instanceId);
    void update(const QString& instanceId, const QString& status, double fraction);
    void end(const QString& instanceId);

    State state(const QString& instanceId) const;

    // The line under the Play button, e.g. "Downloading libraries, 42%, about 12 s left".
    static QString describe(const State& state);
    // Seconds left, from how long the part so far took; -1 while there is too little to go on.
    static int secondsLeft(double fraction, qint64 elapsedMs);

   signals:
    void changed(const QString& instanceId);

   private:
    struct Entry {
        bool active = false;
        QString status;
        double fraction = -1.0;
        QElapsedTimer sinceProgress;
    };
    QHash<QString, Entry> m_entries;
};
