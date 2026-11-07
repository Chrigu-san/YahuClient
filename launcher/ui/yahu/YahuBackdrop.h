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

#include <QPixmap>
#include <QWidget>

// The background behind the launcher: a dark wall of blocks, each a shade lighter or darker, with soft colored light
// from the edges. It doesn't move, so it costs nothing while the launcher is open.
class YahuBackdrop : public QWidget {
    Q_OBJECT
   public:
    explicit YahuBackdrop(QWidget* parent = nullptr);

   protected:
    void paintEvent(QPaintEvent* event) override;

   private:
    QPixmap m_wall;  // drawn once per size
};
