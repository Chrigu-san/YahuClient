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

#include "YahuComingSoon.h"

#include <QMouseEvent>
#include <QPainter>
#include <QTextOption>
#include <QtMath>

#include "YahuStyle.h"

using namespace YahuStyle;

namespace {
constexpr int kPad = 40;
constexpr int kTileWidth = 220;
constexpr int kTileHeight = 236;
constexpr int kTileGap = 18;
constexpr int kPreview = 120;  // height of the animated area inside a tile
constexpr qreal kTau = 6.283185307179586;

qreal easeOut(qreal t)
{
    t = qBound(0.0, t, 1.0);
    return 1.0 - (1.0 - t) * (1.0 - t);
}
}  // namespace

YahuComingSoon::YahuComingSoon(Kind kind, QWidget* parent) : QWidget(parent), m_kind(kind)
{
    setMouseTracking(true);
    setAttribute(Qt::WA_TranslucentBackground, true);
    if (kind == Kind::Servers) {
        m_title = tr("Host your own server");
        m_body = tr("Soon you can create a Minecraft server for you and your friends right here, "
                    "with the same versions and mods as your instances.");
        m_tiles = { { { tr("Pick a version"), tr("Vanilla or modded, any release.") },
                      { tr("Invite friends"), tr("Share it with your crew in a click.") },
                      { tr("Bring your mods"), tr("Use the mods from your instances.") } } };
    } else {
        m_title = tr("Cosmetics shop");
        m_body = tr("Capes, wings and hats to show off in game. The shop is still being built, check back soon.");
        m_tiles = { { { tr("Capes"), tr("Flowing capes in Yahu colors.") },
                      { tr("Wings"), tr("Pixel wings that flap as you fly.") },
                      { tr("Hats"), tr("Crowns, caps and more.") } } };
    }
    m_timer.setInterval(33);
    connect(&m_timer, &QTimer::timeout, this, [this] {
        for (int i = 0; i < 3; ++i) {
            m_tiles[i].hover += ((i == m_hovered ? 1.0 : 0.0) - m_tiles[i].hover) * 0.2;
        }
        update();
    });
    m_clock.start();
}

qreal YahuComingSoon::contentTop() const
{
    // the whole composition is centered vertically, but never closer than kPad to the top
    const qreal total = 30 + 54 + 64 + 30 + kTileHeight;
    return qMax<qreal>(kPad, (height() - total) / 2.0 - 10);
}

QRectF YahuComingSoon::tileRect(int index) const
{
    const qreal rowWidth = kTileWidth * 3 + kTileGap * 2;
    const qreal left = (width() - rowWidth) / 2.0;
    return QRectF(left + index * (kTileWidth + kTileGap), contentTop() + 30 + 54 + 64 + 30, kTileWidth, kTileHeight);
}

void YahuComingSoon::updateRunning()
{
    const bool run = isVisible() && window()->isActiveWindow() && !window()->isMinimized();
    if (run && !m_timer.isActive()) {
        m_timer.start();
    } else if (!run) {
        m_timer.stop();
    }
}

void YahuComingSoon::showEvent(QShowEvent* event)
{
    QWidget::showEvent(event);
    m_clock.restart();  // replays the tile entrance every time the page comes up
    updateRunning();
}

void YahuComingSoon::hideEvent(QHideEvent* event)
{
    QWidget::hideEvent(event);
    m_timer.stop();
}

void YahuComingSoon::changeEvent(QEvent* event)
{
    if (event->type() == QEvent::ActivationChange) {
        updateRunning();
    }
    QWidget::changeEvent(event);
}

void YahuComingSoon::mouseMoveEvent(QMouseEvent* event)
{
    m_cursor = event->position();
    m_hovered = -1;
    for (int i = 0; i < 3; ++i) {
        if (tileRect(i).contains(m_cursor)) {
            m_hovered = i;
        }
    }
    QWidget::mouseMoveEvent(event);
}

void YahuComingSoon::leaveEvent(QEvent* event)
{
    m_hovered = -1;
    m_cursor = QPointF(-1000, -1000);
    QWidget::leaveEvent(event);
}

void YahuComingSoon::paintPreview(QPainter& p, int index, const QRectF& area, qreal t, qreal hover)
{
    const QPointF c = area.center();
    const qreal speed = 1.0 + hover;  // previews get livelier while hovered
    p.setPen(Qt::NoPen);

    if (m_kind == Kind::Shop) {
        if (index == 0) {
            // cape: columns of pixels, each pushed sideways by a travelling wave
            const int cols = 10, rows = 14;
            const qreal px = 6;
            const qreal x0 = c.x() - cols * px / 2;
            const qreal y0 = c.y() - rows * px / 2;
            for (int y = 0; y < rows; ++y) {
                const qreal wave = qSin(t * 3.0 * speed - y * 0.45) * (y / qreal(rows)) * 7.0;
                for (int x = 0; x < cols; ++x) {
                    const bool stripe = x == 4 || x == 5;
                    const bool edge = y == rows - 1 && x % 2 == 0;
                    QColor col = stripe ? gold : mix(accentLo, accent, 0.25 + 0.5 * (x % 3 == 0));
                    if (edge) {
                        col = accentHi;
                    }
                    p.setBrush(col.darker(100 + int(15 * qMax(0.0, qSin(t * 3.0 * speed - y * 0.45)))));
                    p.drawRect(QRectF(x0 + x * px + wave, y0 + y * px, px, px));
                }
            }
            // the shoulder bar it hangs from
            p.setBrush(lineHi);
            p.drawRect(QRectF(x0 - 4, y0 - 6, cols * px + 8, 4));
        } else if (index == 1) {
            // wings: two fans of pixel feathers that flap
            const qreal flap = qSin(t * 4.0 * speed) * 0.5 + 0.5;
            for (int side = -1; side <= 1; side += 2) {
                for (int f = 0; f < 6; ++f) {
                    const qreal angle = qDegreesToRadians(-4.0 - f * 13.0 + flap * 26.0);
                    const qreal len = 56 - f * 5;
                    for (int s = 0; s < int(len / 4); ++s) {
                        const qreal d = 8 + s * 4;
                        const QPointF pt(c.x() + side * qCos(angle) * d, c.y() + qSin(angle) * d + 14);
                        p.setBrush(mix(text, accentHi, f / 6.0 + s * 0.01));
                        p.drawRect(QRectF(pt.x() - 2.5, pt.y() - 2.5, 5, 5));
                    }
                }
            }
            p.setBrush(raisedHi);
            p.drawRoundedRect(QRectF(c.x() - 6, c.y(), 12, 26), 3, 3);
        } else {
            // hat: a pixel crown that bobs and sparkles
            const qreal bob = qSin(t * 2.2 * speed) * 5;
            const qreal px = 6;
            static const char* crown[] = { "1..1..1..1", "11.111.111", "1111111111", "1111111111", "2222222222" };
            const qreal x0 = c.x() - 5 * px;
            const qreal y0 = c.y() - 2.5 * px + bob;
            for (int y = 0; y < 5; ++y) {
                for (int x = 0; x < 10; ++x) {
                    const char ch = crown[y][x];
                    if (ch == '.') {
                        continue;
                    }
                    p.setBrush(ch == '2' ? gold.darker(130) : (x == 4 && y == 2 ? danger : gold));
                    p.drawRect(QRectF(x0 + x * px, y0 + y * px, px, px));
                }
            }
            for (int s = 0; s < 4; ++s) {
                const qreal phase = std::fmod(t * 0.8 * speed + s * 0.25, 1.0);
                const qreal a = qSin(phase * kTau / 2);
                const QPointF sp(c.x() - 40 + s * 27, c.y() - 30 + bob - phase * 16);
                p.setBrush(withAlpha(text, a));
                p.drawRect(QRectF(sp.x() - 1, sp.y() - 4, 2, 8));
                p.drawRect(QRectF(sp.x() - 4, sp.y() - 1, 8, 2));
            }
            // shadow
            p.setBrush(withAlpha(Qt::black, 0.4));
            p.drawEllipse(QPointF(c.x(), c.y() + 34), 30 - bob, 4);
        }
        return;
    }

    if (index == 0) {
        // server rack with blinking lights
        for (int u = 0; u < 3; ++u) {
            const QRectF unit(c.x() - 50, c.y() - 42 + u * 30, 100, 24);
            p.setBrush(raisedHi);
            p.drawRoundedRect(unit, 5, 5);
            p.setBrush(line);
            for (int v = 0; v < 4; ++v) {
                p.drawRect(QRectF(unit.left() + 40 + v * 12, unit.top() + 8, 8, 8));
            }
            for (int l = 0; l < 2; ++l) {
                const bool on = std::fmod(t * speed * (1.3 + u * 0.4 + l * 0.7) + u * 0.3, 1.0) < 0.55;
                const QColor led = l == 0 ? accent : gold;
                p.setBrush(withAlpha(led, on ? 1.0 : 0.2));
                p.drawEllipse(QPointF(unit.left() + 12 + l * 12, unit.center().y()), 3, 3);
                if (on) {
                    p.setBrush(withAlpha(led, 0.2));
                    p.drawEllipse(QPointF(unit.left() + 12 + l * 12, unit.center().y()), 7, 7);
                }
            }
        }
    } else if (index == 1) {
        // three pixel friends hopping one after another
        const std::array<QColor, 3> skins = { QColor(0xC6, 0x8E, 0x62), QColor(0x8D, 0x5A, 0x3B), QColor(0xE0, 0xB0, 0x88) };
        const std::array<QColor, 3> hair = { QColor(0x3B, 0x2A, 0x1E), QColor(0x14, 0x14, 0x14), gold.darker(120) };
        for (int f = 0; f < 3; ++f) {
            const qreal hop = qMax(0.0, qSin(t * 3.0 * speed - f * 0.9)) * 14;
            const QRectF head(c.x() - 54 + f * 38, c.y() - 16 - hop, 32, 32);
            p.setBrush(skins[f]);
            p.drawRect(head);
            p.setBrush(hair[f]);
            p.drawRect(QRectF(head.left(), head.top(), 32, 9));
            p.setBrush(Qt::white);
            p.drawRect(QRectF(head.left() + 6, head.top() + 14, 7, 4));
            p.drawRect(QRectF(head.left() + 19, head.top() + 14, 7, 4));
            p.setBrush(QColor(0x2B, 0x4A, 0x8C));
            p.drawRect(QRectF(head.left() + 9, head.top() + 14, 4, 4));
            p.drawRect(QRectF(head.left() + 19, head.top() + 14, 4, 4));
            p.setBrush(withAlpha(Qt::black, 0.35));
            p.drawEllipse(QPointF(head.center().x(), c.y() + 26), 14 - hop * 0.4, 3);
        }
    } else {
        // a mod "puzzle" cube with pixels orbiting it
        const qreal spin = t * 1.2 * speed;
        const QRectF cube(c.x() - 22, c.y() - 22, 44, 44);
        p.setBrush(mix(accentLo, accent, 0.5));
        p.drawRoundedRect(cube, 6, 6);
        p.setBrush(accentHi);
        p.drawRoundedRect(QRectF(cube.left() + 15, cube.top() - 6, 14, 10), 3, 3);
        p.drawRoundedRect(QRectF(cube.right() - 4, cube.top() + 15, 10, 14), 3, 3);
        for (int k = 0; k < 8; ++k) {
            const qreal a = spin + k * kTau / 8;
            const QPointF pt(c.x() + qCos(a) * 52, c.y() + qSin(a) * 20);
            const bool front = qSin(a) > 0;
            p.setBrush(withAlpha(k % 2 ? gold : accentHi, front ? 1.0 : 0.35));
            p.drawRect(QRectF(pt.x() - 2.5, pt.y() - 2.5, 5, 5));
        }
    }
}

void YahuComingSoon::paintEvent(QPaintEvent*)
{
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing, true);
    const qreal t = m_clock.elapsed() / 1000.0;
    const QColor tone = m_kind == Kind::Shop ? gold : accent;
    const qreal top = contentTop();

    // a slow glow behind the composition
    {
        const QPointF c(width() / 2.0 + qSin(t * 0.4) * 60, top + 170);
        QRadialGradient glow(c, 420);
        glow.setColorAt(0.0, withAlpha(tone, 0.07));
        glow.setColorAt(1.0, withAlpha(tone, 0.0));
        p.fillRect(rect(), glow);
    }

    // a plain line above the title
    {
        QFont small = font();
        small.setWeight(QFont::DemiBold);
        p.setFont(small);
        p.setPen(gold);
        p.drawText(QRectF(kPad, top, width() - kPad * 2, 24), Qt::AlignCenter, tr("Coming soon"));
    }

    // title and body
    {
        QFont title = displayFont(28, QFont::Black);
        p.setFont(title);
        p.setPen(text);
        p.drawText(QRectF(kPad, top + 30, width() - kPad * 2, 54), Qt::AlignCenter, m_title);

        QFont body = font();
        body.setPointSizeF(body.pointSizeF() + 1);
        p.setFont(body);
        p.setPen(muted);
        const qreal bodyWidth = qMin<qreal>(560, width() - kPad * 2);
        QTextOption option(Qt::AlignHCenter | Qt::AlignTop);
        option.setWrapMode(QTextOption::WordWrap);
        p.drawText(QRectF(width() / 2.0 - bodyWidth / 2, top + 30 + 54 + 6, bodyWidth, 58), m_body, option);
    }

    // preview tiles
    for (int i = 0; i < 3; ++i) {
        const Tile& tile = m_tiles[i];
        const qreal hover = easeOut(tile.hover);
        QRectF r = tileRect(i).translated(0, -hover * 6);
        // gentle entrance: tiles float in one after another when the page appears
        const qreal intro = easeOut((t - 0.08 * i) / 0.5);
        p.save();
        p.setOpacity(intro);
        r.translate(0, (1.0 - intro) * 20);

        p.setPen(Qt::NoPen);
        for (int s = 4; s >= 1; --s) {
            p.setBrush(withAlpha(hover > 0.01 ? tone : QColor(Qt::black), hover > 0.01 ? 0.025 * hover : 0.08));
            p.drawPath(rounded(r.adjusted(-s * 2, -s * 2 + 6, s * 2, s * 2 + 6), kRadiusCard + s * 2));
        }
        const QPainterPath shape = rounded(r, kRadiusCard);
        p.fillPath(shape, mix(raised, raisedHi, hover));

        // preview area with its own darker floor and a cursor-following sheen
        const QRectF area(r.left() + 10, r.top() + 10, r.width() - 20, kPreview);
        p.setBrush(withAlpha(ink, 0.7));
        p.drawRoundedRect(area, kRadiusControl, kRadiusControl);
        p.save();
        p.setClipRect(area);
        p.setRenderHint(QPainter::Antialiasing, false);
        paintPreview(p, i, area, t, hover);
        p.restore();
        if (hover > 0.01 && r.contains(m_cursor)) {
            QRadialGradient sheen(m_cursor, r.width() * 0.8);
            sheen.setColorAt(0.0, QColor(255, 255, 255, int(22 * hover)));
            sheen.setColorAt(1.0, QColor(255, 255, 255, 0));
            p.fillPath(shape, sheen);
        }

        p.setBrush(Qt::NoBrush);
        p.setPen(QPen(mix(withAlpha(text, 0.07), withAlpha(tone, 0.6), hover), 1.0));
        p.drawPath(rounded(r.adjusted(0.5, 0.5, -0.5, -0.5), kRadiusCard));

        QFont titleFont = font();
        titleFont.setWeight(QFont::Bold);
        titleFont.setPointSizeF(titleFont.pointSizeF() + 1);
        p.setFont(titleFont);
        p.setPen(text);
        p.drawText(QRectF(r.left() + 18, area.bottom() + 16, r.width() - 36, 22), Qt::AlignLeft | Qt::AlignVCenter, tile.title);
        p.setFont(font());
        p.setPen(muted);
        QTextOption option(Qt::AlignLeft | Qt::AlignTop);
        option.setWrapMode(QTextOption::WordWrap);
        p.drawText(QRectF(r.left() + 18, area.bottom() + 42, r.width() - 36, 44), tile.text, option);

        p.restore();
    }
}
