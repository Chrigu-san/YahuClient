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

#include "YahuSidebar.h"

#include <QAction>
#include <QMenu>
#include <QMouseEvent>
#include <QPainter>
#include <QtMath>

#include "YahuStyle.h"

using namespace YahuStyle;

namespace {
constexpr int kTop = 112;
constexpr int kItemHeight = 46;
constexpr int kPad = 14;
constexpr int kAccountHeight = 66;
constexpr qreal kTau = 6.283185307179586;
}  // namespace

YahuSidebar::YahuSidebar(QWidget* parent) : QWidget(parent)
{
    setMouseTracking(true);
    setFixedWidth(232);
    setCursor(Qt::ArrowCursor);
    m_timer.setInterval(16);
    connect(&m_timer, &QTimer::timeout, this, &YahuSidebar::tick);
    m_clock.start();
}

void YahuSidebar::addAction(QAction* action, const QString& label, const QString& iconName)
{
    m_items.append({ label, iconName, action, nullptr, {}, false });
    connect(action, &QAction::changed, this, QOverload<>::of(&QWidget::update));
    update();
}

void YahuSidebar::addMenu(QMenu* menu, const QString& label, const QString& iconName)
{
    m_items.append({ label, iconName, nullptr, menu, {}, false });
    update();
}

void YahuSidebar::addPage(const QString& label, const QString& iconName, std::function<void()> onClick)
{
    m_items.append({ label, iconName, nullptr, nullptr, std::move(onClick), true });
    if (m_activePage < 0) {
        m_activePage = m_items.size() - 1;
    }
    update();
}

void YahuSidebar::setAccountAction(QAction* action, QMenu* menu)
{
    m_accountAction = action;
    m_accountMenu = menu;
    connect(action, &QAction::changed, this, QOverload<>::of(&QWidget::update));
    update();
}

QRectF YahuSidebar::itemRect(int index) const
{
    return QRectF(kPad, kTop + index * kItemHeight, width() - kPad * 2, kItemHeight - 6);
}

QRectF YahuSidebar::accountRect() const
{
    return QRectF(kPad, height() - kAccountHeight - kPad, width() - kPad * 2, kAccountHeight);
}

int YahuSidebar::hitTest(const QPoint& pos) const
{
    for (int i = 0; i < m_items.size(); ++i) {
        if (itemRect(i).contains(pos)) {
            return i;
        }
    }
    if (m_accountAction && accountRect().contains(pos)) {
        return -2;
    }
    return -1;
}

void YahuSidebar::ensureTicking()
{
    if (!m_timer.isActive()) {
        m_timer.start();
    }
}

void YahuSidebar::tick()
{
    bool moving = false;
    auto approach = [&moving](qreal& value, qreal target, qreal speed) {
        const qreal next = value + (target - value) * speed;
        if (qAbs(next - target) < 0.002) {
            value = target;
        } else {
            value = next;
            moving = true;
        }
    };

    for (int i = 0; i < m_items.size(); ++i) {
        approach(m_items[i].hover, i == m_hovered ? 1.0 : 0.0, 0.2);
        approach(m_items[i].flash, 0.0, 0.08);
    }
    if (m_hovered >= 0) {
        const qreal target = itemRect(m_hovered).top();
        if (m_slabY < 0) {
            m_slabY = target;
        }
        approach(m_slabY, target, 0.25);
    }
    approach(m_slabAlpha, m_hovered >= 0 ? 1.0 : 0.0, 0.15);
    approach(m_accountHover, m_hovered == -2 ? 1.0 : 0.0, 0.2);

    update();
    // the account chip's status pixel breathes, so keep a slow heartbeat going while visible
    if (!moving && !isVisible()) {
        m_timer.stop();
    } else if (!moving) {
        m_timer.setInterval(66);
    } else {
        m_timer.setInterval(16);
    }
}

void YahuSidebar::changeEvent(QEvent* event)
{
    if (event->type() == QEvent::ActivationChange) {
        if (window()->isActiveWindow()) {
            ensureTicking();
        } else {
            m_timer.stop();
        }
    }
    QWidget::changeEvent(event);
}

void YahuSidebar::mouseMoveEvent(QMouseEvent* event)
{
    const int hit = hitTest(event->position().toPoint());
    if (hit != m_hovered) {
        m_hovered = hit;
        setCursor(hit == -1 ? Qt::ArrowCursor : Qt::PointingHandCursor);
        ensureTicking();
    }
    QWidget::mouseMoveEvent(event);
}

void YahuSidebar::leaveEvent(QEvent* event)
{
    m_hovered = -1;
    ensureTicking();
    QWidget::leaveEvent(event);
}

void YahuSidebar::mousePressEvent(QMouseEvent* event)
{
    m_pressed = hitTest(event->position().toPoint());
    QWidget::mousePressEvent(event);
}

void YahuSidebar::mouseReleaseEvent(QMouseEvent* event)
{
    const int hit = hitTest(event->position().toPoint());
    if (hit == m_pressed && event->button() == Qt::LeftButton) {
        activate(hit);
    }
    m_pressed = -1;
    QWidget::mouseReleaseEvent(event);
}

void YahuSidebar::activate(int index)
{
    if (index == -2) {
        if (m_accountMenu) {
            const QRectF r = accountRect();
            m_accountMenu->popup(mapToGlobal(QPoint(int(r.right()) + 8, int(r.bottom()) - m_accountMenu->sizeHint().height())));
        }
        return;
    }
    if (index < 0 || index >= m_items.size()) {
        return;
    }
    Item& item = m_items[index];
    item.flash = 1.0;
    ensureTicking();
    if (item.page) {
        m_activePage = index;
        if (item.onClick) {
            item.onClick();
        }
    } else if (item.menu) {
        const QRectF r = itemRect(index);
        item.menu->popup(mapToGlobal(QPoint(int(r.right()) + 8, int(r.top()))));
    } else if (item.action && item.action->isEnabled()) {
        item.action->trigger();
    }
}

void YahuSidebar::paintEvent(QPaintEvent*)
{
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing, true);
    const qreal t = m_clock.elapsed() / 1000.0;

    // panel with a hairline on the right
    p.fillRect(rect(), withAlpha(surface, 0.86));
    p.fillRect(QRectF(width() - 1, 0, 1, height()), line);

    // brand
    {
        const QIcon logo(QStringLiteral(":/yahu/logo.svg"));
        logo.paint(&p, QRect(kPad + 4, 30, 40, 40));
        p.setFont(displayFont(17, QFont::Black));
        p.setPen(text);
        p.drawText(QPointF(kPad + 56, 54), QStringLiteral("YAHU"));
        p.setFont(pixelFont(8));
        p.setPen(accent);
        p.drawText(QPointF(kPad + 57, 68), QStringLiteral("CLIENT"));
    }

    p.setFont(pixelFont(8));
    p.setPen(faint);
    p.drawText(QPointF(kPad + 4, kTop - 12), tr("MENU"));

    // the gliding hover slab
    if (m_slabAlpha > 0.01 && m_slabY >= 0) {
        const QRectF slab(kPad, m_slabY, width() - kPad * 2, kItemHeight - 6);
        p.setPen(Qt::NoPen);
        p.setBrush(withAlpha(raisedHi, m_slabAlpha));
        p.drawPath(chamfer(slab, 7));
    }

    for (int i = 0; i < m_items.size(); ++i) {
        const Item& item = m_items[i];
        const QRectF r = itemRect(i);
        const bool active = item.page && i == m_activePage;
        const bool enabled = !item.action || item.action->isEnabled();

        if (active) {
            p.setPen(Qt::NoPen);
            p.setBrush(withAlpha(accent, 0.10));
            p.drawPath(chamfer(r, 7));
            // pixel bracket on the left edge
            p.fillRect(QRectF(r.left(), r.top() + 8, 3, r.height() - 16), accent);
            p.fillRect(QRectF(r.left() + 3, r.top() + 8, 3, 3), accent);
            p.fillRect(QRectF(r.left() + 3, r.bottom() - 11, 3, 3), accent);
        }
        if (item.flash > 0.01) {
            p.setPen(Qt::NoPen);
            p.setBrush(withAlpha(accentHi, 0.25 * item.flash));
            p.drawPath(chamfer(r, 7));
        }

        const qreal nudge = item.hover * 4.0;
        const QRect iconRect(int(r.left() + 14 + nudge), int(r.center().y() - 10), 20, 20);
        QIcon::fromTheme(item.iconName).paint(&p, iconRect, Qt::AlignCenter, enabled ? QIcon::Normal : QIcon::Disabled);

        QFont font = this->font();
        font.setWeight(active ? QFont::Bold : QFont::DemiBold);
        p.setFont(font);
        p.setPen(!enabled ? faint : mix(active ? text : muted, text, item.hover));
        p.drawText(QRectF(r.left() + 46 + nudge, r.top(), r.width() - 56, r.height()), Qt::AlignVCenter | Qt::AlignLeft, item.label);

        if (item.menu) {
            p.setPen(QPen(withAlpha(muted, 0.5 + 0.5 * item.hover), 1.6));
            const qreal x = r.right() - 16 + item.hover * 3;
            const qreal y = r.center().y();
            p.drawLine(QPointF(x - 3, y - 4), QPointF(x + 1, y));
            p.drawLine(QPointF(x + 1, y), QPointF(x - 3, y + 4));
        }
    }

    // account chip
    if (m_accountAction) {
        const QRectF r = accountRect();
        p.setPen(QPen(mix(line, accent, m_accountHover), 1.0));
        p.setBrush(mix(raised, raisedHi, m_accountHover));
        p.drawPath(chamfer(r.adjusted(0.5, 0.5, -0.5, -0.5), 9));

        const QRect face(int(r.left() + 12), int(r.center().y() - 18), 36, 36);
        p.save();
        p.setRenderHint(QPainter::SmoothPixmapTransform, false);  // keep skin faces crisp
        m_accountAction->icon().paint(&p, face);
        p.restore();

        const bool signedIn = m_accountAction->text() != tr("Accounts");
        QFont nameFont = font();
        nameFont.setWeight(QFont::Bold);
        p.setFont(nameFont);
        p.setPen(text);
        const QString name = signedIn ? m_accountAction->text() : tr("Sign in");
        p.drawText(QRectF(face.right() + 12, r.top() + 13, r.width() - 70, 20), Qt::AlignLeft | Qt::AlignVCenter,
                   QFontMetrics(nameFont).elidedText(name, Qt::ElideRight, int(r.width() - 74)));

        const qreal pulse = 0.5 + 0.5 * qSin(t * kTau / 2.4);
        const QColor statusColor = signedIn ? accent : gold;
        p.setPen(Qt::NoPen);
        p.setBrush(withAlpha(statusColor, 0.35 + 0.65 * pulse));
        p.drawRect(QRectF(face.right() + 12, r.top() + 41, 5, 5));
        p.setFont(pixelFont(8));
        p.setPen(statusColor);
        p.drawText(QPointF(face.right() + 22, r.top() + 47), signedIn ? tr("ONLINE") : tr("NO ACCOUNT"));
    }
}
