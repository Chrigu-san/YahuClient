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
#include <QList>
#include <QPointF>
#include <QTimer>
#include <QWidget>

// The living background behind the launcher: a night meadow of drifting firefly pixels that flock towards the
// cursor, and a soft lantern that follows the mouse and reveals the pixel grid underneath.
class YahuBackdrop : public QWidget {
    Q_OBJECT
   public:
    explicit YahuBackdrop(QWidget* parent = nullptr);

   protected:
    void paintEvent(QPaintEvent* event) override;
    void resizeEvent(QResizeEvent* event) override;
    void showEvent(QShowEvent* event) override;
    void hideEvent(QHideEvent* event) override;
    bool eventFilter(QObject* watched, QEvent* event) override;

   private:
    struct Firefly {
        QPointF pos;
        QPointF vel;
        qreal phase;
        qreal size;
        bool gold;
    };

    void tick();
    void seed();
    void updateRunning();

    QList<Firefly> m_flies;
    QPointF m_cursor{ -1000, -1000 };  // smoothed lantern position
    QPointF m_cursorTarget{ -1000, -1000 };
    qreal m_lantern = 0.0;  // fades in while the mouse is over the window
    QTimer m_timer;
    QElapsedTimer m_clock;
    QElapsedTimer m_lastMove;
};
