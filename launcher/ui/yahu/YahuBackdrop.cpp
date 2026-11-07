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

#include "YahuBackdrop.h"

#include <QPainter>

#include "YahuStyle.h"

using namespace YahuStyle;

YahuBackdrop::YahuBackdrop(QWidget* parent) : QWidget(parent)
{
    setAttribute(Qt::WA_OpaquePaintEvent, true);
}

void YahuBackdrop::paintEvent(QPaintEvent*)
{
    QPainter p(this);
    if (m_wall.size() != size()) {
        m_wall = QPixmap(size());
        m_wall.fill(ink);
        QPainter w(&m_wall);

        // the wall of blocks
        constexpr int kBlock = 32;
        for (int y = 0; y < height(); y += kBlock) {
            for (int x = 0; x < width(); x += kBlock) {
                const uint h = uint(x / kBlock) * 73856093u ^ uint(y / kBlock) * 19349663u;
                const qreal shade = ((h >> 4) % 100) / 100.0;
                w.fillRect(QRect(x, y, kBlock, kBlock), mix(ink, shade > 0.5 ? surface : Qt::black, 0.12 + 0.18 * qAbs(shade - 0.5)));
                w.fillRect(QRect(x, y, kBlock, 1), withAlpha(Qt::white, 0.025));
                w.fillRect(QRect(x, y + kBlock - 1, kBlock, 1), withAlpha(Qt::black, 0.18));
            }
        }

        // colored light: grass rising from the bottom, sky in one corner, amethyst in the other
        const QRectF r = rect();
        QRadialGradient floor(QPointF(r.center().x(), r.bottom() + r.height() * 0.2), r.width() * 0.7);
        floor.setColorAt(0.0, withAlpha(accent, 0.08));
        floor.setColorAt(1.0, withAlpha(accent, 0.0));
        w.fillRect(r, floor);
        QRadialGradient cool(QPointF(r.right(), r.top()), r.width() * 0.5);
        cool.setColorAt(0.0, withAlpha(sky, 0.07));
        cool.setColorAt(1.0, withAlpha(sky, 0.0));
        w.fillRect(r, cool);
        QRadialGradient warm(QPointF(r.left(), r.bottom()), r.width() * 0.45);
        warm.setColorAt(0.0, withAlpha(amethyst, 0.06));
        warm.setColorAt(1.0, withAlpha(amethyst, 0.0));
        w.fillRect(r, warm);
    }
    p.drawPixmap(0, 0, m_wall);
}
