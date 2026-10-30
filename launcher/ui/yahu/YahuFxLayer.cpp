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
#include <QRandomGenerator>
#include <QtMath>

#include "YahuStyle.h"

using namespace YahuStyle;

namespace {
QPointer<YahuFxLayer> s_layer;
constexpr qreal kTau = 6.283185307179586;
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

void YahuFxLayer::burst(const QPoint& globalPos, int sparks)
{
    if (s_layer) {
        s_layer->spawnBurst(s_layer->mapFromGlobal(globalPos), sparks, 70.0);
    }
}

void YahuFxLayer::spawnBurst(const QPointF& at, int sparks, qreal ringSize)
{
    auto* rng = QRandomGenerator::global();
    m_rings.append({ at, 0.0, ringSize });
    for (int i = 0; i < sparks; ++i) {
        const qreal angle = rng->generateDouble() * kTau;
        const qreal speed = 1.5 + rng->generateDouble() * 3.5;
        const int pick = rng->bounded(6);
        m_particles.append({ at, QPointF(qCos(angle), qSin(angle)) * speed - QPointF(0, 1.2), 1.0,
                             0.022 + rng->generateDouble() * 0.02, rng->bounded(2) ? 3.0 : 2.0,
                             pick == 0 ? gold : (pick == 1 ? text : accentHi) });
    }
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
            spawnBurst(mapFromGlobal(me->globalPosition()), 9, 26.0);
        }
    }
    return false;
}

void YahuFxLayer::tick()
{
    const qreal step = qMin<qreal>(m_frame.restart(), 40.0) / 16.0;
    for (auto& p : m_particles) {
        p.pos += p.vel * step;
        p.vel.setY(p.vel.y() + 0.12 * step * (p.size > 2 ? 1.0 : 0.4));  // gravity, heavier sparks fall faster
        p.vel *= 0.97;
        p.life -= p.decay * step;
    }
    m_particles.erase(std::remove_if(m_particles.begin(), m_particles.end(), [](const Particle& p) { return p.life <= 0.0; }),
                      m_particles.end());
    for (auto& r : m_rings) {
        r.t += 0.045 * step;
    }
    m_rings.erase(std::remove_if(m_rings.begin(), m_rings.end(), [](const Ring& r) { return r.t >= 1.0; }), m_rings.end());

    const QRect dirty = dirtyRect();
    update(dirty.united(m_lastDirty));
    m_lastDirty = dirty;
    if (m_particles.isEmpty() && m_rings.isEmpty()) {
        m_timer.stop();
    }
}

QRect YahuFxLayer::dirtyRect() const
{
    QRectF bounds;
    for (const auto& p : m_particles) {
        bounds |= QRectF(p.pos - QPointF(4, 4), QSizeF(8, 8));
    }
    for (const auto& r : m_rings) {
        const qreal radius = r.maxRadius * r.t + 4;
        bounds |= QRectF(r.center - QPointF(radius, radius), QSizeF(radius * 2, radius * 2));
    }
    return bounds.toAlignedRect();
}

void YahuFxLayer::paintEvent(QPaintEvent*)
{
    QPainter p(this);
    for (const auto& r : m_rings) {
        // a square shockwave that grows and fades, drawn in pixel steps
        const qreal ease = 1.0 - qPow(1.0 - r.t, 3);
        const qreal radius = r.maxRadius * ease;
        p.setPen(QPen(withAlpha(accent, (1.0 - r.t) * 0.8), 2.0, Qt::SolidLine, Qt::SquareCap, Qt::MiterJoin));
        p.setBrush(Qt::NoBrush);
        p.setRenderHint(QPainter::Antialiasing, true);
        p.drawPath(chamfer(QRectF(r.center.x() - radius, r.center.y() - radius, radius * 2, radius * 2), radius * 0.35));
    }
    p.setRenderHint(QPainter::Antialiasing, false);
    p.setPen(Qt::NoPen);
    for (const auto& part : m_particles) {
        p.setBrush(withAlpha(part.color, qMin(1.0, part.life * 1.3)));
        p.drawRect(QRectF(part.pos.x(), part.pos.y(), part.size, part.size));
    }
}
