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

#include "YahuPlayButton.h"

#include <QPainter>
#include <QPainterPath>
#include <QtMath>

#include "YahuFxLayer.h"
#include "YahuStyle.h"

namespace {
constexpr qreal kCut = 12.0;
constexpr qreal kBreathPeriodMs = 2400.0;
constexpr int kGlowPad = 12;  // room around the face for the glow
constexpr qreal kTau = 6.283185307179586;
}  // namespace

YahuPlayButton::YahuPlayButton(QWidget* parent) : QAbstractButton(parent)
{
    setCursor(Qt::PointingHandCursor);
    setFocusPolicy(Qt::StrongFocus);
    setAttribute(Qt::WA_Hover, true);

    connect(&m_hoverAnimation, &QVariantAnimation::valueChanged, this, [this](const QVariant& v) {
        m_hover = v.toReal();
        update();
    });
    connect(&m_pressAnimation, &QVariantAnimation::valueChanged, this, [this](const QVariant& v) {
        m_press = v.toReal();
        update();
    });
    connect(&m_shineAnimation, &QVariantAnimation::valueChanged, this, [this](const QVariant& v) {
        m_shine = v.toReal();
        update();
    });
    connect(this, &QAbstractButton::pressed, this, [this] { animateTo(m_pressAnimation, m_press, 1.0, 80); });
    connect(this, &QAbstractButton::released, this, [this] { animateTo(m_pressAnimation, m_press, 0.0, 320); });
    connect(this, &QAbstractButton::clicked, this, [this] { YahuFxLayer::burst(mapToGlobal(rect().center()), 40); });

    m_breathTimer.setInterval(33);
    connect(&m_breathTimer, &QTimer::timeout, this, QOverload<>::of(&QWidget::update));
    m_clock.start();
}

QSize YahuPlayButton::sizeHint() const
{
    // wide enough for the longest label of the current mode, but never narrower than the classic PLAY key
    QFont font = YahuStyle::displayFont(13, QFont::Black);
    font.setLetterSpacing(QFont::AbsoluteSpacing, 1.5);
    const QString label = m_mode == Mode::Create ? tr("NEW INSTANCE") : tr("PLAYING");
    const int content = QFontMetrics(font).horizontalAdvance(label) + 14 + 12;
    return { qMax(210, content + 64) + kGlowPad * 2, 56 + kGlowPad * 2 };
}

void YahuPlayButton::setMode(Mode mode)
{
    if (m_mode == mode) {
        return;
    }
    m_mode = mode;
    updateGeometry();
    updateBreathing();
    update();
}

void YahuPlayButton::animateTo(QVariantAnimation& animation, qreal& current, qreal target, int durationMs)
{
    animation.stop();
    animation.setStartValue(current);
    animation.setEndValue(target);
    animation.setDuration(durationMs);
    animation.setEasingCurve(target > current ? QEasingCurve::OutCubic : QEasingCurve::OutBack);
    animation.start();
}

void YahuPlayButton::enterEvent(QEnterEvent* event)
{
    animateTo(m_hoverAnimation, m_hover, 1.0, 200);
    if (isEnabled()) {
        m_shineAnimation.stop();
        m_shineAnimation.setStartValue(0.0);
        m_shineAnimation.setEndValue(1.0);
        m_shineAnimation.setDuration(650);
        m_shineAnimation.setEasingCurve(QEasingCurve::InOutQuad);
        m_shineAnimation.start();
    }
    QAbstractButton::enterEvent(event);
}

void YahuPlayButton::leaveEvent(QEvent* event)
{
    animateTo(m_hoverAnimation, m_hover, 0.0, 280);
    QAbstractButton::leaveEvent(event);
}

void YahuPlayButton::changeEvent(QEvent* event)
{
    if (event->type() == QEvent::EnabledChange) {
        updateBreathing();
        update();
    }
    QAbstractButton::changeEvent(event);
}

void YahuPlayButton::showEvent(QShowEvent* event)
{
    updateBreathing();
    QAbstractButton::showEvent(event);
}

void YahuPlayButton::hideEvent(QHideEvent* event)
{
    m_breathTimer.stop();
    QAbstractButton::hideEvent(event);
}

void YahuPlayButton::updateBreathing()
{
    if (isVisible() && (isEnabled() || m_mode == Mode::Running)) {
        m_breathTimer.start();
    } else {
        m_breathTimer.stop();
    }
}

void YahuPlayButton::paintEvent(QPaintEvent*)
{
    using namespace YahuStyle;
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing, true);

    const bool running = m_mode == Mode::Running;
    const bool active = isEnabled() || running;
    const qreal now = m_clock.elapsed();
    const qreal breath = active ? 0.5 + 0.5 * qSin(now / kBreathPeriodMs * kTau) : 0.0;

    QRectF face = QRectF(rect()).adjusted(kGlowPad, kGlowPad, -kGlowPad, -kGlowPad);
    // the face sinks into its "base" when pressed, like a physical key
    const qreal depth = 5.0;
    const qreal sink = depth * m_press;
    face.adjust(0, sink - m_hover * 1.5, 0, -depth + sink - m_hover * 1.5);

    const QColor tone = running ? gold : accent;
    const QColor toneHi = running ? gold.lighter(112) : accentHi;
    const QColor toneLo = running ? QColor(0xB8, 0x8A, 0x1E) : QColor(0x2F, 0x9C, 0x3E);

    // glow
    if (active) {
        const qreal strength = 0.25 + 0.25 * breath + 0.4 * m_hover;
        for (int i = kGlowPad; i >= 2; i -= 2) {
            p.setPen(Qt::NoPen);
            p.setBrush(withAlpha(tone, strength * 0.05 * (kGlowPad - i + 2) / 2.0 * 0.6));
            p.drawPath(chamfer(face.adjusted(-i, -i + 4, i, i + 4 + depth), kCut + i * 0.6));
        }
    }

    // key base (the darker block under the face)
    const QRectF base = QRectF(face.left(), face.top() + depth - sink, face.width(), face.height());
    p.setPen(Qt::NoPen);
    p.setBrush(active ? toneLo : line);
    p.drawPath(chamfer(base, kCut));

    const QPainterPath shape = chamfer(face, kCut);
    if (active && !running) {
        QLinearGradient fill(face.topLeft(), face.bottomLeft());
        fill.setColorAt(0.0, mix(tone, toneHi, 0.4 + 0.6 * m_hover));
        fill.setColorAt(1.0, mix(tone, toneHi, m_hover * 0.5));
        p.fillPath(shape, fill);
    } else if (running) {
        p.fillPath(shape, mix(raised, gold, 0.18));
        p.setPen(QPen(gold, 1.5));
        p.drawPath(shape);
    } else {
        p.fillPath(shape, raised);
        p.setPen(QPen(lineHi, 1.0));
        p.drawPath(shape);
    }

    // pixel shine sweep: a diagonal band of squares crossing the face
    if (active && !running && m_shine < 1.0) {
        p.save();
        p.setClipPath(shape);
        p.setPen(Qt::NoPen);
        const qreal x = face.left() - 60 + (face.width() + 120) * m_shine;
        for (int row = 0; row < int(face.height() / 4) + 1; ++row) {
            const qreal y = face.top() + row * 4;
            const qreal shift = (face.bottom() - y) * 0.6;
            p.setBrush(QColor(255, 255, 255, 70));
            p.drawRect(QRectF(x + shift, y, 14, 4));
            p.setBrush(QColor(255, 255, 255, 35));
            p.drawRect(QRectF(x + shift + 18, y, 6, 4));
        }
        p.restore();
    }

    // label
    const QColor labelColor = active && !running ? ink : (running ? gold : faint);
    QFont font = displayFont(13, QFont::Black);
    font.setLetterSpacing(QFont::AbsoluteSpacing, 1.5);
    p.setFont(font);
    const QString label = running ? tr("PLAYING") : (m_mode == Mode::Create ? tr("NEW INSTANCE") : tr("PLAY"));
    const QFontMetricsF fm(font);
    const qreal glyph = 14.0;
    const qreal gap = 12.0;
    const qreal contentWidth = glyph + gap + fm.horizontalAdvance(label);
    const qreal startX = face.center().x() - contentWidth / 2.0;
    const qreal cy = face.center().y();

    p.setPen(Qt::NoPen);
    if (running) {
        // equalizer bars bouncing while the game runs
        for (int i = 0; i < 3; ++i) {
            const qreal h = 5 + 8 * (0.5 + 0.5 * qSin(now / 180.0 + i * 1.7));
            p.setBrush(gold);
            p.drawRect(QRectF(startX + i * 5, cy + 7 - h, 3, h));
        }
    } else if (m_mode == Mode::Create) {
        // pixel plus that turns a quarter on hover
        p.save();
        p.translate(startX + glyph / 2, cy);
        p.rotate(m_hover * 90.0);
        p.setBrush(labelColor);
        p.drawRect(QRectF(-7, -2, 14, 4));
        p.drawRect(QRectF(-2, -7, 4, 14));
        p.restore();
    } else {
        // pixel play triangle, slides forward on hover
        p.setBrush(labelColor);
        const qreal px = startX + m_hover * 3;
        for (int i = 0; i < 4; ++i) {
            const qreal h = 16 - i * 4;
            p.drawRect(QRectF(px + i * 3.5, cy - h / 2, 3.5, h));
        }
    }

    p.setPen(labelColor);
    p.drawText(QRectF(startX + glyph + gap, face.top(), fm.horizontalAdvance(label) + 4, face.height()), Qt::AlignVCenter | Qt::AlignLeft,
               label);

    if (hasFocus()) {
        p.setPen(QPen(YahuStyle::text, 1.0, Qt::DotLine));
        p.setBrush(Qt::NoBrush);
        p.drawPath(chamfer(face.adjusted(-4, -4, 4, 4 + depth), kCut + 3));
    }
}
