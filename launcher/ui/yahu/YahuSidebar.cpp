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
#include <QHelpEvent>
#include <QMenu>
#include <QMouseEvent>
#include <QPainter>
#include <QToolTip>
#include <QtMath>

#include "YahuStyle.h"

using namespace YahuStyle;

namespace {
constexpr int kTop = 88;          // first page below the logo
constexpr int kItemHeight = 60;
constexpr int kTile = 64;         // width of the hover tile
constexpr int kAccountSize = 44;
constexpr int kAccountGap = 18;   // space below the avatar
constexpr qreal kTau = 6.283185307179586;
}  // namespace

YahuSidebar::YahuSidebar(QWidget* parent) : QWidget(parent)
{
    setMouseTracking(true);
    setFixedWidth(kWidth);
    setCursor(Qt::ArrowCursor);
    m_timer.setInterval(16);
    connect(&m_timer, &QTimer::timeout, this, &YahuSidebar::tick);
    m_clock.start();
}

void YahuSidebar::addPage(const QString& label, const QString& iconName, std::function<void()> onClick, const QString& badge)
{
    Item item{ label, iconName, nullptr, nullptr, std::move(onClick), badge, true, m_addingBottom };
    m_items.append(item);
    if (m_activePage < 0) {
        m_activePage = m_items.size() - 1;
    }
    update();
}

void YahuSidebar::addAction(QAction* action, const QString& label, const QString& iconName)
{
    m_items.append({ label, iconName, action, nullptr, {}, QString(), false, m_addingBottom });
    connect(action, &QAction::changed, this, QOverload<>::of(&QWidget::update));
    update();
}

void YahuSidebar::addMenu(QMenu* menu, const QString& label, const QString& iconName)
{
    m_items.append({ label, iconName, nullptr, menu, {}, QString(), false, m_addingBottom });
    update();
}

void YahuSidebar::beginBottomGroup()
{
    m_addingBottom = true;
}

void YahuSidebar::setAccountAction(QAction* action, QMenu* menu, std::function<bool()> hasAccounts, std::function<void()> signIn)
{
    m_accountAction = action;
    m_accountMenu = menu;
    m_hasAccounts = std::move(hasAccounts);
    m_signIn = std::move(signIn);
    connect(action, &QAction::changed, this, QOverload<>::of(&QWidget::update));
    update();
}

void YahuSidebar::setActivePage(int pageIndex)
{
    int seen = 0;
    for (int i = 0; i < m_items.size(); ++i) {
        if (m_items[i].page && seen++ == pageIndex) {
            m_activePage = i;
            ensureTicking();
            return;
        }
    }
}

QRectF YahuSidebar::itemRect(int index) const
{
    const qreal x = (kWidth - kTile) / 2.0;
    if (!m_items[index].bottom) {
        int row = 0;
        for (int i = 0; i < index; ++i) {
            row += m_items[i].bottom ? 0 : 1;
        }
        return QRectF(x, kTop + row * kItemHeight, kTile, kItemHeight - 6);
    }
    // bottom group: stacked upwards from the avatar
    int below = 0;
    for (int i = index + 1; i < m_items.size(); ++i) {
        below += m_items[i].bottom ? 1 : 0;
    }
    const qreal bottom = accountRect().top() - 18 - below * kItemHeight;
    return QRectF(x, bottom - (kItemHeight - 6), kTile, kItemHeight - 6);
}

QRectF YahuSidebar::accountRect() const
{
    return QRectF((kWidth - kAccountSize) / 2.0, height() - kAccountSize - kAccountGap, kAccountSize, kAccountSize);
}

int YahuSidebar::hitTest(const QPoint& pos) const
{
    for (int i = 0; i < m_items.size(); ++i) {
        if (itemRect(i).contains(pos)) {
            return i;
        }
    }
    if (m_accountAction && accountRect().adjusted(-6, -6, 6, 6).contains(pos)) {
        return -2;
    }
    return -1;
}

bool YahuSidebar::signedIn() const
{
    return m_hasAccounts ? m_hasAccounts() : (m_accountAction && m_accountAction->text() != tr("Accounts"));
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
        if (m_slabY < 0 || m_slabAlpha < 0.02) {
            m_slabY = target;
        }
        approach(m_slabY, target, 0.28);
    }
    approach(m_slabAlpha, m_hovered >= 0 ? 1.0 : 0.0, 0.15);
    if (m_activePage >= 0) {
        const qreal target = itemRect(m_activePage).center().y();
        if (m_markerY < 0) {
            m_markerY = target;
        }
        approach(m_markerY, target, 0.18);
    }
    approach(m_accountHover, m_hovered == -2 ? 1.0 : 0.0, 0.2);

    update();
    // the avatar's status dot breathes, so keep a slow heartbeat going while visible
    if (!moving && !isVisible()) {
        m_timer.stop();
    } else {
        m_timer.setInterval(moving ? 16 : 66);
    }
}

bool YahuSidebar::event(QEvent* event)
{
    if (event->type() == QEvent::ToolTip) {
        auto* help = static_cast<QHelpEvent*>(event);
        const int hit = hitTest(help->pos());
        QString tip;
        if (hit == -2) {
            tip = signedIn() ? m_accountAction->text() : tr("Sign in with Microsoft");
        } else if (hit >= 0 && !m_items[hit].badge.isEmpty()) {
            tip = tr("%1 (%2)").arg(m_items[hit].label, m_items[hit].badge);
        }
        if (tip.isEmpty()) {
            QToolTip::hideText();
            event->ignore();
        } else {
            QToolTip::showText(help->globalPos(), tip, this);
        }
        return true;
    }
    return QWidget::event(event);
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
        if (m_signIn && !signedIn()) {
            m_signIn();
        } else if (m_accountMenu) {
            const QRectF r = accountRect();
            m_accountMenu->popup(mapToGlobal(QPoint(int(r.right()) + 14, int(r.bottom()) - m_accountMenu->sizeHint().height())));
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
        const int y = item.bottom ? int(r.bottom()) - item.menu->sizeHint().height() : int(r.top());
        item.menu->popup(mapToGlobal(QPoint(int(r.right()) + 14, y)));
    } else if (item.action && item.action->isEnabled()) {
        item.action->trigger();
    }
}

void YahuSidebar::paintEvent(QPaintEvent*)
{
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing, true);
    const qreal t = m_clock.elapsed() / 1000.0;

    // rail with a hairline on the right
    p.fillRect(rect(), surface);
    p.fillRect(QRectF(width() - 1, 0, 1, height()), line);

    // logo, with a faint grass glow behind it
    {
        const QPointF c(kWidth / 2.0, 42);
        QRadialGradient glow(c, 34);
        glow.setColorAt(0.0, withAlpha(accent, 0.16));
        glow.setColorAt(1.0, withAlpha(accent, 0.0));
        p.setPen(Qt::NoPen);
        p.setBrush(glow);
        p.drawEllipse(c, 34, 34);
        QIcon(QStringLiteral(":/yahu/logo.svg")).paint(&p, QRect(int(c.x()) - 18, int(c.y()) - 18, 36, 36));
    }

    // separator between the pages and the rest
    p.fillRect(QRectF(22, kTop - 14, kWidth - 44, 1), line);

    // the gliding hover tile
    if (m_slabAlpha > 0.01 && m_slabY >= 0) {
        const QRectF slab((kWidth - kTile) / 2.0, m_slabY, kTile, kItemHeight - 6);
        p.setPen(Qt::NoPen);
        p.setBrush(withAlpha(raised, m_slabAlpha));
        p.drawPath(rounded(slab, 12));
    }

    // the active-page marker: a green pill hugging the left edge
    if (m_activePage >= 0 && m_markerY >= 0) {
        p.setPen(Qt::NoPen);
        p.setBrush(accent);
        p.drawRoundedRect(QRectF(-3, m_markerY - 14, 7, 28), 3.5, 3.5);
        QRadialGradient glow(QPointF(0, m_markerY), 30);
        glow.setColorAt(0.0, withAlpha(accent, 0.25));
        glow.setColorAt(1.0, withAlpha(accent, 0.0));
        p.setBrush(glow);
        p.drawEllipse(QPointF(0, m_markerY), 30, 30);
    }

    for (int i = 0; i < m_items.size(); ++i) {
        const Item& item = m_items[i];
        const QRectF r = itemRect(i);
        const bool active = item.page && i == m_activePage;
        const bool enabled = !item.action || item.action->isEnabled();

        if (active) {
            p.setPen(Qt::NoPen);
            p.setBrush(raisedHi);
            p.drawPath(rounded(r, 12));
        }
        if (item.flash > 0.01) {
            p.setPen(Qt::NoPen);
            p.setBrush(withAlpha(accent, 0.18 * item.flash));
            p.drawPath(rounded(r, 12));
        }

        const qreal lift = item.hover * 2.0;
        const QRect iconRect(int(r.center().x() - 11), int(r.top() + 9 - lift), 22, 22);
        p.save();
        if (!active && enabled) {
            p.setOpacity(0.72 + 0.28 * item.hover);
        }
        QIcon::fromTheme(item.iconName).paint(&p, iconRect, Qt::AlignCenter, enabled ? QIcon::Normal : QIcon::Disabled);
        p.restore();

        QFont font = this->font();
        font.setPointSizeF(font.pointSizeF() - 1.5);
        font.setWeight(active ? QFont::Bold : QFont::DemiBold);
        p.setFont(font);
        p.setPen(!enabled ? faint : mix(active ? text : muted, text, item.hover));
        p.drawText(QRectF(r.left() - 6, r.top() + 34, r.width() + 12, 16), Qt::AlignHCenter | Qt::AlignVCenter, item.label);

        if (!item.badge.isEmpty()) {
            // a small gold dot that says "something is coming here"
            const QPointF dot(r.center().x() + 13, r.top() + 10);
            p.setPen(Qt::NoPen);
            p.setBrush(gold);
            p.drawEllipse(dot, 3, 3);
        }

        if (item.menu) {
            // tiny chevron to the right: this opens a menu
            p.setPen(QPen(withAlpha(muted, 0.35 + 0.65 * item.hover), 1.4));
            const qreal x = r.right() - 5 + item.hover * 2;
            const qreal y = r.top() + 20;
            p.drawLine(QPointF(x - 2, y - 3), QPointF(x + 1, y));
            p.drawLine(QPointF(x + 1, y), QPointF(x - 2, y + 3));
        }
    }

    // avatar
    if (m_accountAction) {
        const QRectF r = accountRect();
        const bool in = signedIn();
        const QColor statusColor = in ? accent : gold;

        // hover ring
        p.setPen(QPen(withAlpha(statusColor, 0.25 + 0.75 * m_accountHover), 2.0));
        p.setBrush(Qt::NoBrush);
        p.drawPath(rounded(r.adjusted(-4, -4, 4, 4), 14));

        p.setPen(Qt::NoPen);
        p.setBrush(raised);
        p.drawPath(rounded(r, 11));
        p.save();
        p.setClipPath(rounded(r, 11));
        p.setRenderHint(QPainter::SmoothPixmapTransform, false);  // keep skin faces crisp
        if (in) {
            m_accountAction->icon().paint(&p, r.toRect());
        } else {
            QIcon::fromTheme(QStringLiteral("accounts")).paint(&p, r.adjusted(10, 10, -10, -10).toRect());
        }
        p.restore();

        // status dot, bottom right, breathing
        const qreal pulse = 0.5 + 0.5 * qSin(t * kTau / 2.4);
        const QPointF dot(r.right() - 1, r.bottom() - 1);
        p.setBrush(surface);
        p.drawEllipse(dot, 7, 7);
        p.setBrush(withAlpha(statusColor, 0.25 * pulse));
        p.drawEllipse(dot, 6.5, 6.5);
        p.setBrush(statusColor);
        p.drawEllipse(dot, 4, 4);
    }
}
