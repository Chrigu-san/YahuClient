// SPDX-License-Identifier: GPL-3.0-only
/*
 *  Yahu Client - based on Prism Launcher
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

#include <QObject>

class QWidget;

// App-wide motion: windows and dialogs fade in when they first appear, menus and tooltips fade,
// and pages can be revealed with a short cross-fade.
class YahuAnimator : public QObject {
    Q_OBJECT
   public:
    static void install(QObject* parent);

    // Fades a child widget in from transparent. Safe to call repeatedly.
    static void reveal(QWidget* widget, int durationMs = 200);

   protected:
    bool eventFilter(QObject* watched, QEvent* event) override;

   private:
    explicit YahuAnimator(QObject* parent) : QObject(parent) {}
};
