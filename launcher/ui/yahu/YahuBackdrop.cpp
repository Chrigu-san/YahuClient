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

#include <QApplication>
#include <QMouseEvent>
#include <QPainter>
#include <QRandomGenerator>
#include <QtMath>

#include "YahuSheetHost.h"
#include "YahuStyle.h"

using namespace YahuStyle;

namespace {
constexpr qreal kScareRadius = 150.0;  // fireflies within this distance of a moving cursor scatter
constexpr qreal kTau = 6.283185307179586;
}  // namespace

YahuBackdrop::YahuBackdrop(QWidget* parent) : QWidget(parent)
{
    setAttribute(Qt::WA_OpaquePaintEvent, true);
    m_timer.setInterval(33);
    m_timer.setTimerType(Qt::PreciseTimer);
    connect(&m_timer, &QTimer::timeout, this, &YahuBackdrop::tick);
    m_clock.start();
    m_lastMove.start();
    qApp->installEventFilter(this);
}

void YahuBackdrop::seed()
{
    auto* rng = QRandomGenerator::global();
    const int count = qBound(16, width() * height() / 40000, 44);
    m_flies.clear();
    for (int i = 0; i < count; ++i) {
        Firefly f;
        f.pos = QPointF(rng->bounded(qMax(1, width())), rng->bounded(qMax(1, height())));
        f.vel = QPointF((rng->generateDouble() - 0.5) * 0.4, -0.15 - rng->generateDouble() * 0.35);
        f.phase = rng->generateDouble() * kTau;
        f.size = rng->bounded(3) == 0 ? 3.0 : 2.0;
        f.gold = rng->bounded(4) == 0;
        m_flies.append(f);
    }
}

void YahuBackdrop::resizeEvent(QResizeEvent* event)
{
    QWidget::resizeEvent(event);
    if (m_flies.isEmpty() || qAbs(m_flies.size() - width() * height() / 40000) > 8) {
        seed();
    }
}

void YahuBackdrop::showEvent(QShowEvent* event)
{
    QWidget::showEvent(event);
    updateRunning();
}

void YahuBackdrop::hideEvent(QHideEvent* event)
{
    QWidget::hideEvent(event);
    m_timer.stop();
}

void YahuBackdrop::updateRunning()
{
    // Nothing moves while the launcher is in the background (for example while the game is focused).
    const bool run = isVisible() && window()->isActiveWindow() && !window()->isMinimized();
    if (run && !m_timer.isActive()) {
        m_timer.start();
    } else if (!run && m_timer.isActive()) {
        m_timer.stop();
        update();
    }
}

bool YahuBackdrop::eventFilter(QObject* watched, QEvent* event)
{
    switch (event->type()) {
        case QEvent::MouseMove: {
            auto* w = qobject_cast<QWidget*>(watched);
            if (w && w->window() == window()) {
                m_cursorTarget = mapFromGlobal(static_cast<QMouseEvent*>(event)->globalPosition());
                if (m_cursor.x() < -500) {
                    m_cursor = m_cursorTarget;
                }
                m_lastMove.restart();
            }
            break;
        }
        case QEvent::WindowActivate:
        case QEvent::WindowDeactivate:
        case QEvent::WindowStateChange:
            if (watched == window()) {
                updateRunning();
            }
            break;
        case QEvent::Leave:
            if (watched == window()) {
                m_cursorTarget = QPointF(-1000, -1000);
            }
            break;
        default:
            break;
    }
    return false;
}

void YahuBackdrop::tick()
{
    if (auto* host = YahuSheetHost::active(); host && host->coversWindow()) {
        return;  // a full page covers the meadow; save the work
    }
    const qreal t = m_clock.elapsed() / 1000.0;
    const bool cursorInside = rect().contains(m_cursorTarget.toPoint());
    // how fast the mouse moved since the last tick; a resting cursor doesn't bother anyone
    QPointF sweep(0, 0);
    if (cursorInside && m_cursor.x() > -500) {
        sweep = m_cursorTarget - m_cursor;
    }
    m_cursor = cursorInside ? m_cursorTarget : QPointF(-1000, -1000);
    const qreal sweepSpeed = qSqrt(sweep.x() * sweep.x() + sweep.y() * sweep.y());

    for (auto& f : m_flies) {
        // lazy drift upwards with a little sway
        QPointF accel(qSin(t * 0.7 + f.phase) * 0.012, qCos(t * 0.5 + f.phase) * 0.006);
        if (cursorInside && sweepSpeed > 1.0) {
            // fireflies get startled by a passing cursor and dart away, partly along its path
            const QPointF d = f.pos - m_cursor;
            const qreal dist = qMax(1.0, qSqrt(d.x() * d.x() + d.y() * d.y()));
            if (dist < kScareRadius) {
                const qreal strength = (1.0 - dist / kScareRadius) * qMin(1.0, sweepSpeed / 30.0);
                accel += d / dist * 0.9 * strength + sweep / sweepSpeed * 0.35 * strength;
            }
        }
        f.vel = (f.vel + accel) * 0.985;
        // keep a minimum upward drift so the meadow never freezes
        f.vel.setY(qMin(f.vel.y(), 0.25));
        f.pos += f.vel;
        if (f.pos.y() < -10) {
            f.pos.setY(height() + 10);
            f.pos.setX(QRandomGenerator::global()->bounded(qMax(1, width())));
        }
        if (f.pos.x() < -10) {
            f.pos.setX(width() + 10);
        } else if (f.pos.x() > width() + 10) {
            f.pos.setX(-10);
        }
    }
    update();
}

void YahuBackdrop::paintEvent(QPaintEvent*)
{
    QPainter p(this);
    const QRectF r = rect();
    const qreal t = m_clock.elapsed() / 1000.0;

    // deep black with the faintest green glow rising from the bottom
    p.fillRect(r, ink);
    QRadialGradient floor(QPointF(r.center().x(), r.bottom() + r.height() * 0.2), r.width() * 0.7);
    floor.setColorAt(0.0, withAlpha(accent, 0.045));
    floor.setColorAt(1.0, withAlpha(accent, 0.0));
    p.fillRect(r, floor);

    // fireflies
    p.setPen(Qt::NoPen);
    for (const auto& f : m_flies) {
        const qreal twinkle = 0.45 + 0.55 * (0.5 + 0.5 * qSin(t * 2.2 + f.phase * 3.0));
        // a startled firefly flashes brighter while it flies fast, then calms down again
        const qreal rush = qMin(1.0, qSqrt(f.vel.x() * f.vel.x() + f.vel.y() * f.vel.y()) / 3.0);
        const QColor c = f.gold ? gold : accentHi;
        const qreal alpha = qMin(1.0, 0.15 + 0.35 * twinkle + 0.6 * rush);
        // halo
        p.setBrush(withAlpha(c, alpha * 0.18));
        p.drawRect(QRectF(f.pos.x() - f.size * 1.5, f.pos.y() - f.size * 1.5, f.size * 4, f.size * 4));
        p.setBrush(withAlpha(c, alpha));
        p.drawRect(QRectF(f.pos.x(), f.pos.y(), f.size, f.size));
    }
}
