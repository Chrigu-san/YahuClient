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

#include <QAbstractButton>
#include <QElapsedTimer>
#include <QTimer>
#include <QVariantAnimation>

// The big launch key: breathes while idle, sweeps a pixel shine across on hover and sinks in when pressed.
class YahuPlayButton : public QAbstractButton {
    Q_OBJECT
   public:
    enum class Mode { Play, Running };

    explicit YahuPlayButton(QWidget* parent = nullptr);

    void setMode(Mode mode);
    Mode mode() const { return m_mode; }

    QSize sizeHint() const override;

   protected:
    void paintEvent(QPaintEvent* event) override;
    void enterEvent(QEnterEvent* event) override;
    void leaveEvent(QEvent* event) override;
    void changeEvent(QEvent* event) override;
    void showEvent(QShowEvent* event) override;
    void hideEvent(QHideEvent* event) override;

   private:
    void animateTo(QVariantAnimation& animation, qreal& current, qreal target, int durationMs);
    void updateBreathing();

    Mode m_mode = Mode::Play;
    qreal m_hover = 0.0;
    qreal m_press = 0.0;
    qreal m_shine = 1.0;
    QVariantAnimation m_hoverAnimation;
    QVariantAnimation m_pressAnimation;
    QVariantAnimation m_shineAnimation;
    QTimer m_breathTimer;
    QElapsedTimer m_clock;
};
