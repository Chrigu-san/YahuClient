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
#include <QVariantAnimation>
#include <QWidget>

class BaseInstance;
class QAction;
class QMenu;
class QToolButton;
class YahuPlayButton;

// The banner above the library: a pixel night landscape that shifts with the mouse, a floating grass block,
// the selected instance in big type and the launch controls. It drives the launcher's own actions, so it behaves like the toolbar did.
class YahuHero : public QWidget {
    Q_OBJECT
   public:
    YahuHero(QAction* launchAction,
             QAction* killAction,
             QAction* editAction,
             QAction* folderAction,
             const QList<QAction*>& moreActions,
             QWidget* parent = nullptr);

    void setInstance(BaseInstance* instance);
    // With an empty library the big button creates an instance instead of launching one.
    void setCreateAction(QAction* createAction);
    // Re-reads name, icon and state from the current instance.
    void refresh();

   protected:
    void paintEvent(QPaintEvent* event) override;
    void showEvent(QShowEvent* event) override;
    void hideEvent(QHideEvent* event) override;
    void changeEvent(QEvent* event) override;
    bool eventFilter(QObject* watched, QEvent* event) override;

   private:
    void syncActions();
    void updateRunning();
    void paintBlock(QPainter& p, const QPointF& center, qreal size, qreal yaw, qreal pitch);
    void paintLandscape(QPainter& p, const QRectF& banner, qreal t);
    QRectF bannerRect() const;
    QToolButton* makeGhostButton(const QString& label, const QString& iconName);

    QPointer<BaseInstance> m_instance;
    QAction* m_launchAction;
    QAction* m_killAction;
    QAction* m_editAction;
    QAction* m_folderAction;
    QAction* m_createAction = nullptr;
    QMenu* m_moreMenu = nullptr;

    YahuPlayButton* m_play = nullptr;
    QToolButton* m_launchMenuButton = nullptr;
    QToolButton* m_stopButton = nullptr;
    QToolButton* m_editButton = nullptr;
    QToolButton* m_folderButton = nullptr;
    QToolButton* m_moreButton = nullptr;

    QString m_title;
    QString m_meta;
    QIcon m_icon;
    bool m_running = false;
    bool m_libraryEmpty = false;

    qreal m_swap = 1.0;       // 0 -> 1 when the selection changes; drives the text slide-in and a block spin
    qreal m_spin = 0.0;       // extra yaw added by the selection spin
    QVariantAnimation m_swapAnimation;
    QPointF m_look;           // smoothed cursor direction (-1..1), tilts the block
    QPointF m_lookTarget;
    QTimer m_frameTimer;
    QElapsedTimer m_clock;
};
