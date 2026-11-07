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

// A click-through layer on top of the whole window for a small, quick click effect: a pixel square that opens up
// around the click and fades.
class YahuFxLayer : public QWidget {
    Q_OBJECT
   public:
    explicit YahuFxLayer(QWidget* window);

    // A bigger square for something that matters (e.g. pressing Play), at a global position.
    static void burst(const QPoint& globalPos);

   protected:
    void paintEvent(QPaintEvent* event) override;
    bool nativeEvent(const QByteArray& eventType, void* message, qintptr* result) override;
    bool eventFilter(QObject* watched, QEvent* event) override;

   private:
    struct Ring {
        QPointF center;
        qreal t;  // 0 -> 1
        qreal maxRadius;
    };

    void spawnRing(const QPointF& at, qreal size);
    void tick();
    QRect dirtyRect() const;

    QList<Ring> m_rings;
    QRect m_lastDirty;
    QTimer m_timer;
    QElapsedTimer m_frame;
};
