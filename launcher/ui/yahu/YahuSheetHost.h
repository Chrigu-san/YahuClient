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

#include <QHash>
#include <QObject>
#include <QPointer>

class QWidget;

// Keeps Yahu Client feeling like a single-window app. Dialogs and secondary windows stay real Qt windows
// (so modal dialogs, exec() and window ownership keep working), but they lose their frame and are laid
// exactly over the main window's content, styled as a full page (settings, instance editor) or as a centered
// card over a dimmed backdrop (message boxes, small dialogs). They follow the main window when it moves.
class YahuSheetHost : public QObject {
    Q_OBJECT
   public:
    explicit YahuSheetHost(QWidget* mainWindow);

    // The host of the main window, if it is up and visible.
    static YahuSheetHost* active();

    // Called for every top-level widget that is being polished, i.e. before it is first shown.
    static bool wantsToAdopt(QWidget* topLevel);
    void adopt(QWidget* topLevel);

    // True while a full page hides the main window's content.
    bool coversWindow() const;

   protected:
    bool eventFilter(QObject* watched, QEvent* event) override;

   private:
    struct Sheet {
        bool page = false;
        QSize natural;
        qreal enter = 1.0;  // 0 -> 1 while appearing
        QWidget* closeButton = nullptr;
    };

    void place(QWidget* w);
    void appear(QWidget* w);
    void paintChrome(QWidget* w);
    QRect overlayRect() const;

    QWidget* m_window;
    QHash<QWidget*, Sheet> m_sheets;
    static QPointer<YahuSheetHost> s_active;
};
