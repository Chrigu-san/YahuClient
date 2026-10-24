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

#include "YahuAnimator.h"

#include <QApplication>
#include <QDialogButtonBox>
#include <QPushButton>
#include <QEvent>
#include <QGraphicsOpacityEffect>
#include <QPointer>
#include <QPropertyAnimation>
#include <QTimer>
#include <QWidget>

#include "YahuSheetHost.h"

namespace {
constexpr const char* kFadedInProperty = "_yahu_faded_in";

bool wantsWindowFade(QWidget* widget)
{
    if (!widget->isWindow() || widget->property(kFadedInProperty).toBool()) {
        return false;
    }
    switch (widget->windowType()) {
        case Qt::Window:
        case Qt::Dialog:
        case Qt::Sheet:
            return !widget->testAttribute(Qt::WA_DontShowOnScreen);
        default:
            return false;  // popups, tooltips, splash screens and tool windows keep their own behaviour
    }
}
}  // namespace

void YahuAnimator::install(QObject* parent)
{
    QApplication::setEffectEnabled(Qt::UI_AnimateMenu, false);
    QApplication::setEffectEnabled(Qt::UI_FadeMenu, true);
    QApplication::setEffectEnabled(Qt::UI_AnimateCombo, true);
    QApplication::setEffectEnabled(Qt::UI_AnimateTooltip, false);
    QApplication::setEffectEnabled(Qt::UI_FadeTooltip, true);
    qApp->installEventFilter(new YahuAnimator(parent));
}

bool YahuAnimator::eventFilter(QObject* watched, QEvent* event)
{
    if (!watched->isWidgetType()) {
        return QObject::eventFilter(watched, event);
    }
    auto* widget = static_cast<QWidget*>(watched);

    // Windows that open on top of the main window become overlays before they are ever shown.
    if (event->type() == QEvent::Polish && YahuSheetHost::wantsToAdopt(widget)) {
        YahuSheetHost::active()->adopt(widget);
    }

    if (event->type() == QEvent::Show && !widget->property("yahuOverlay").toBool()) {
        if (wantsWindowFade(widget)) {
            widget->setProperty(kFadedInProperty, true);
            widget->setWindowOpacity(0.0);
            auto* animation = new QPropertyAnimation(widget, "windowOpacity", widget);
            animation->setDuration(widget->inherits("QMainWindow") ? 360 : 190);
            animation->setStartValue(0.0);
            animation->setEndValue(1.0);
            animation->setEasingCurve(QEasingCurve::OutCubic);
            animation->start(QAbstractAnimation::DeleteWhenStopped);
        }
    }
    // Yahu Client has no online help pages, so dialog "Help" buttons go away.
    if (event->type() == QEvent::Show) {
        if (auto* box = qobject_cast<QDialogButtonBox*>(widget)) {
            if (auto* help = box->button(QDialogButtonBox::Help)) {
                help->hide();
            }
        }
    }
    return QObject::eventFilter(watched, event);
}

void YahuAnimator::reveal(QWidget* widget, int durationMs)
{
    if (!widget || !widget->isVisible()) {
        return;
    }
    // Don't stack effects on widgets that already have one (e.g. a running reveal or a custom effect).
    if (widget->graphicsEffect() && !widget->graphicsEffect()->property("_yahu_reveal").toBool()) {
        return;
    }
    auto* effect = qobject_cast<QGraphicsOpacityEffect*>(widget->graphicsEffect());
    if (!effect) {
        effect = new QGraphicsOpacityEffect(widget);
        effect->setProperty("_yahu_reveal", true);
        widget->setGraphicsEffect(effect);
    }
    effect->setOpacity(0.0);

    auto* animation = new QPropertyAnimation(effect, "opacity", effect);
    animation->setDuration(durationMs);
    animation->setStartValue(0.0);
    animation->setEndValue(1.0);
    animation->setEasingCurve(QEasingCurve::OutCubic);
    QPointer<QWidget> guard(widget);
    QObject::connect(animation, &QPropertyAnimation::finished, widget, [guard] {
        // Opacity effects render offscreen; drop it once fully visible so the page paints natively again.
        if (guard) {
            guard->setGraphicsEffect(nullptr);
        }
    });
    animation->start(QAbstractAnimation::DeleteWhenStopped);
}
