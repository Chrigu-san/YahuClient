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

#include <QElapsedTimer>
#include <QIcon>
#include <QList>
#include <QPointer>
#include <QTimer>
#include <QWidget>
#include <functional>

class QAction;
class QMenu;

// The navigation column on the left. Everything is painted by hand so hovering feels alive: a glowing
// slab glides after the cursor, icons nudge forward and the account chip at the bottom breathes.
class YahuSidebar : public QWidget {
    Q_OBJECT
   public:
    explicit YahuSidebar(QWidget* parent = nullptr);

    // An entry that triggers an action when clicked.
    void addAction(QAction* action, const QString& label, const QString& iconName);
    // An entry that pops up a menu next to itself.
    void addMenu(QMenu* menu, const QString& label, const QString& iconName);
    // The entry that stands for the page currently on screen.
    void addPage(const QString& label, const QString& iconName, std::function<void()> onClick);

    // The account chip at the bottom mirrors this action (its text and face icon) and opens its menu.
    void setAccountAction(QAction* action, QMenu* menu);

    QSize sizeHint() const override { return { 232, 600 }; }

   protected:
    void paintEvent(QPaintEvent* event) override;
    void mouseMoveEvent(QMouseEvent* event) override;
    void mousePressEvent(QMouseEvent* event) override;
    void mouseReleaseEvent(QMouseEvent* event) override;
    void leaveEvent(QEvent* event) override;
    void changeEvent(QEvent* event) override;

   private:
    struct Item {
        QString label;
        QString iconName;
        QPointer<QAction> action;
        QPointer<QMenu> menu;
        std::function<void()> onClick;
        bool page = false;
        qreal hover = 0.0;
        qreal flash = 0.0;
    };

    QRectF itemRect(int index) const;
    QRectF accountRect() const;
    int hitTest(const QPoint& pos) const;  // item index, -2 for the account chip, -1 for nothing
    void activate(int index);
    void ensureTicking();
    void tick();

    QList<Item> m_items;
    int m_hovered = -1;
    int m_pressed = -1;
    int m_activePage = -1;
    qreal m_slabY = -1;      // glides towards the hovered item
    qreal m_slabAlpha = 0;
    qreal m_accountHover = 0;
    QPointer<QAction> m_accountAction;
    QPointer<QMenu> m_accountMenu;
    QTimer m_timer;
    QElapsedTimer m_clock;
};
