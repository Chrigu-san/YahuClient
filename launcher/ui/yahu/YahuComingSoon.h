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
#include <QTimer>
#include <QWidget>

#include <array>

// A page for a feature that is still being built (server hosting, the cosmetics shop). It says what is coming
// and shows three small animated previews, so the tab already feels like part of the launcher.
class YahuComingSoon : public QWidget {
    Q_OBJECT
   public:
    enum class Kind { Servers, Shop };

    explicit YahuComingSoon(Kind kind, QWidget* parent = nullptr);

   protected:
    void paintEvent(QPaintEvent* event) override;
    void mouseMoveEvent(QMouseEvent* event) override;
    void leaveEvent(QEvent* event) override;
    void showEvent(QShowEvent* event) override;
    void hideEvent(QHideEvent* event) override;
    void changeEvent(QEvent* event) override;

   private:
    struct Tile {
        QString title;
        QString text;
        qreal hover = 0.0;
    };

    QRectF tileRect(int index) const;
    qreal contentTop() const;
    void paintPreview(QPainter& p, int index, const QRectF& area, qreal t, qreal hover);
    void updateRunning();

    Kind m_kind;
    QString m_title;
    QString m_body;
    std::array<Tile, 3> m_tiles;
    int m_hovered = -1;
    QPointF m_cursor{ -1000, -1000 };
    QTimer m_timer;
    QElapsedTimer m_clock;
};
