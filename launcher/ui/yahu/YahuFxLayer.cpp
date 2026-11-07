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

#include "YahuFxLayer.h"

#include <QApplication>
#include <QMouseEvent>
#include <QPainter>
#include <QPointer>
#include <QtMath>

#include <cmath>

#include "YahuStyle.h"

#ifdef Q_OS_WIN
#include <windows.h>
#endif

using namespace YahuStyle;

namespace {
QPointer<YahuFxLayer> s_layer;
}  // namespace

YahuFxLayer::YahuFxLayer(QWidget* window) : QWidget(window)
{
    setAttribute(Qt::WA_TransparentForMouseEvents, true);
    setAttribute(Qt::WA_NoSystemBackground, true);
    setFocusPolicy(Qt::NoFocus);
    setGeometry(window->rect());
    window->installEventFilter(this);
    qApp->installEventFilter(this);
    m_timer.setInterval(16);
    m_timer.setTimerType(Qt::PreciseTimer);
    connect(&m_timer, &QTimer::timeout, this, &YahuFxLayer::tick);
    s_layer = this;
}

void YahuFxLayer::burst(const QPoint& globalPos)
{
    if (s_layer) {
        s_layer->spawnRing(s_layer->mapFromGlobal(globalPos), 40.0);
    }
}

void YahuFxLayer::spawnRing(const QPointF& at, qreal size)
{
    m_rings.append({ at, 0.0, size });
    raise();
    if (!m_timer.isActive()) {
        m_frame.start();
        m_timer.start();
    }
}

bool YahuFxLayer::eventFilter(QObject* watched, QEvent* event)
{
    if (watched == parent() && event->type() == QEvent::Resize) {
        setGeometry(parentWidget()->rect());
        return false;
    }
    auto* w = qobject_cast<QWidget*>(watched);
    if (!w || w->window() != window()) {
        return false;
    }
    if (event->type() == QEvent::MouseButtonPress) {
        auto* me = static_cast<QMouseEvent*>(event);
        // one burst per click, not one per widget in the propagation chain
        if (me->spontaneous() && w == QApplication::widgetAt(me->globalPosition().toPoint())) {
            spawnRing(mapFromGlobal(me->globalPosition()), 14.0);
        }
    }
    return false;
}

bool YahuFxLayer::nativeEvent(const QByteArray& eventType, void* message, qintptr* result)
{
#ifdef Q_OS_WIN
    // WA_TransparentForMouseEvents only works while the layer has no window of its own; if it gets one, tell
    // Windows to send the mouse to whatever lies underneath
    if (eventType == "windows_generic_MSG" && static_cast<MSG*>(message)->message == WM_NCHITTEST) {
        *result = HTTRANSPARENT;
        return true;
    }
#endif
    return QWidget::nativeEvent(eventType, message, result);
}

void YahuFxLayer::tick()
{
    const qreal step = qMin<qreal>(m_frame.restart(), 40.0) / 16.0;
    for (auto& r : m_rings) {
        r.t += 0.075 * step;  // about a quarter of a second
    }
    m_rings.erase(std::remove_if(m_rings.begin(), m_rings.end(), [](const Ring& r) { return r.t >= 1.0; }), m_rings.end());

    const QRect dirty = dirtyRect();
    update(dirty.united(m_lastDirty));
    m_lastDirty = dirty;
    if (m_rings.isEmpty()) {
        m_timer.stop();
    }
}

QRect YahuFxLayer::dirtyRect() const
{
    QRectF bounds;
    for (const auto& r : m_rings) {
        const qreal radius = r.maxRadius * r.t + 4;
        bounds |= QRectF(r.center - QPointF(radius, radius), QSizeF(radius * 2, radius * 2));
    }
    return bounds.toAlignedRect();
}

void YahuFxLayer::paintEvent(QPaintEvent*)
{
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing, false);
    for (const auto& r : m_rings) {
        // a square outline that opens up and fades; its size snaps to 2px so it steps like pixels
        const qreal ease = 1.0 - qPow(1.0 - r.t, 3);
        const qreal radius = std::round((4 + (r.maxRadius - 4) * ease) / 2) * 2;
        const QColor c = withAlpha(Qt::white, (1.0 - r.t) * 0.7);
        const QRectF box(std::round(r.center.x()) - radius, std::round(r.center.y()) - radius, radius * 2, radius * 2);
        p.fillRect(QRectF(box.left(), box.top(), box.width(), 2), c);
        p.fillRect(QRectF(box.left(), box.bottom() - 2, box.width(), 2), c);
        p.fillRect(QRectF(box.left(), box.top() + 2, 2, box.height() - 4), c);
        p.fillRect(QRectF(box.right() - 2, box.top() + 2, 2, box.height() - 4), c);
    }
}
