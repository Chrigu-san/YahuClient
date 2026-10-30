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

// The navigation rail on the left: the logo, the pages, the tools and the player's face at the bottom.
// Everything is painted by hand so it feels alive: a soft tile glides after the cursor, the green marker
// slides to the page on screen and the status dot on the avatar breathes.
class YahuSidebar : public QWidget {
    Q_OBJECT
   public:
    explicit YahuSidebar(QWidget* parent = nullptr);

    // A page of the launcher; exactly one page is marked as the one on screen. A badge (e.g. "Soon") is shown next to it.
    void addPage(const QString& label, const QString& iconName, std::function<void()> onClick, const QString& badge = QString());
    // An entry that triggers an action when clicked.
    void addAction(QAction* action, const QString& label, const QString& iconName);
    // An entry that pops up a menu next to itself.
    void addMenu(QMenu* menu, const QString& label, const QString& iconName);
    // Entries added after this call sit at the bottom of the rail, just above the avatar.
    void beginBottomGroup();

    // The avatar at the bottom mirrors this action (its text and face icon). With accounts it opens the menu,
    // without any it starts the sign-in right away.
    void setAccountAction(QAction* action, QMenu* menu, std::function<bool()> hasAccounts, std::function<void()> signIn);

    void setActivePage(int pageIndex);

    QSize sizeHint() const override { return { kWidth, 600 }; }
    QSize minimumSizeHint() const override { return { kWidth, 560 }; }

    static constexpr int kWidth = 88;

   protected:
    bool event(QEvent* event) override;
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
        QString badge;
        bool page = false;
        bool bottom = false;
        qreal hover = 0.0;
        qreal flash = 0.0;
    };

    QRectF itemRect(int index) const;
    QRectF accountRect() const;
    int hitTest(const QPoint& pos) const;  // item index, -2 for the avatar, -1 for nothing
    void activate(int index);
    void ensureTicking();
    void tick();
    bool signedIn() const;

    QList<Item> m_items;
    bool m_addingBottom = false;
    int m_hovered = -1;
    int m_pressed = -1;
    int m_activePage = -1;
    qreal m_slabY = -1;    // glides towards the hovered item
    qreal m_slabAlpha = 0;
    qreal m_markerY = -1;  // glides towards the active page
    qreal m_accountHover = 0;
    QPointer<QAction> m_accountAction;
    QPointer<QMenu> m_accountMenu;
    std::function<bool()> m_hasAccounts;
    std::function<void()> m_signIn;
    QTimer m_timer;
    QElapsedTimer m_clock;
};
