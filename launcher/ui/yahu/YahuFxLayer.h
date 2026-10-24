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

#include <QColor>
#include <QElapsedTimer>
#include <QList>
#include <QPointF>
#include <QTimer>
#include <QWidget>

// A click-through layer on top of the whole window for small bursts of motion: every click pops a pixel
// shockwave with sparks, and moving the mouse leaves a short trail of fading pixels.
class YahuFxLayer : public QWidget {
    Q_OBJECT
   public:
    explicit YahuFxLayer(QWidget* window);

    // Celebrate something (e.g. pressing Play) with a bigger burst at a global position.
    static void burst(const QPoint& globalPos, int sparks = 26);

   protected:
    void paintEvent(QPaintEvent* event) override;
    bool eventFilter(QObject* watched, QEvent* event) override;

   private:
    struct Particle {
        QPointF pos;
        QPointF vel;
        qreal life;  // 1 -> 0
        qreal decay;
        qreal size;
        QColor color;
    };
    struct Ring {
        QPointF center;
        qreal t;  // 0 -> 1
        qreal maxRadius;
    };

    void spawnBurst(const QPointF& at, int sparks, qreal ringSize);
    void tick();
    QRect dirtyRect() const;

    QList<Particle> m_particles;
    QList<Ring> m_rings;
    QPointF m_lastTrail{ -1000, -1000 };
    QRect m_lastDirty;
    QTimer m_timer;
    QElapsedTimer m_frame;
};
