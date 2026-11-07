// SPDX-License-Identifier: GPL-3.0-only
/*
 *  Prism Launcher - Minecraft Launcher
 *  Copyright (C) 2022 Sefa Eyeoglu <contact@scrumplex.net>
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
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 *      Copyright 2013-2021 MultiMC Contributors
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

#include "InstanceDelegate.h"
#include <QApplication>
#include <QDebug>
#include <QPainter>
#include <QTextLayout>
#include <QTextOption>
#include <QtMath>

#include <QIcon>
#include <QTextEdit>
#include "BaseInstance.h"
#include "InstanceList.h"
#include "InstanceView.h"
#include "MMCTime.h"
#include "ui/yahu/YahuStyle.h"

// Origin: Qt
static void viewItemTextLayout(QTextLayout& textLayout, int lineWidth, qreal& height, qreal& widthUsed)
{
    height = 0;
    widthUsed = 0;
    textLayout.beginLayout();
    QString str = textLayout.text();
    while (true) {
        QTextLine line = textLayout.createLine();
        if (!line.isValid())
            break;
        if (line.textLength() == 0)
            break;
        line.setLineWidth(lineWidth);
        line.setPosition(QPointF(0, height));
        height += line.height();
        widthUsed = qMax(widthUsed, line.naturalTextWidth());
    }
    textLayout.endLayout();
}

ListViewDelegate::ListViewDelegate(QObject* parent) : QStyledItemDelegate(parent) {}

namespace {
// Card metrics. The top inset leaves room for the hover lift so it never gets clipped.
constexpr int kLift = 6;
constexpr int kPadTop = 16;
constexpr int kIconSize = 56;
constexpr int kGap = 12;
constexpr int kMetaHeight = 16;
constexpr int kPadBottom = 14;
constexpr int kTextInset = 12;
constexpr qreal kRadius = 14.0;

qreal easeOutCubic(qreal t)
{
    t = qBound(0.0, t, 1.0);
    return 1.0 - qPow(1.0 - t, 3);
}

QFont cardFont(const QStyleOptionViewItem& option)
{
    QFont font = option.font;
    font.setWeight(QFont::Bold);
    return font;
}

void drawStatusPixel(QPainter* painter, const QPointF& center, const QColor& color, qreal pulse)
{
    // pulse in [0, 1): an expanding, fading square around a solid pixel
    const qreal ring = 4.0 + 7.0 * pulse;
    painter->setPen(QPen(YahuStyle::withAlpha(color, 0.7 * (1.0 - pulse)), 1.5));
    painter->setBrush(Qt::NoBrush);
    painter->drawRect(QRectF(center.x() - ring, center.y() - ring, ring * 2, ring * 2));
    painter->setPen(Qt::NoPen);
    painter->setBrush(color);
    painter->drawRect(QRectF(center.x() - 4, center.y() - 4, 8, 8));
}
}  // namespace

static QSize viewItemTextSize(const QStyleOptionViewItem* option)
{
    QTextOption textOption;
    textOption.setWrapMode(QTextOption::WrapAtWordBoundaryOrAnywhere);
    QTextLayout textLayout;
    textLayout.setTextOption(textOption);
    textLayout.setFont(cardFont(*option));
    textLayout.setText(option->text);
    QRect bounds(0, 0, InstanceView::kCardWidth - 2 * kTextInset, 600);
    qreal height = 0, widthUsed = 0;
    viewItemTextLayout(textLayout, bounds.width(), height, widthUsed);
    return QSize(qCeil(widthUsed), qCeil(height));
}

void ListViewDelegate::paint(QPainter* painter, const QStyleOptionViewItem& option, const QModelIndex& index) const
{
    using namespace YahuStyle;

    QStyleOptionViewItem opt = option;
    initStyleOption(&opt, index);
    opt.text = index.data().toString();

    const auto* view = qobject_cast<const InstanceView*>(opt.widget);
    const qreal hover = view ? easeOutCubic(view->hoverProgress(index)) : ((opt.state & QStyle::State_MouseOver) ? 1.0 : 0.0);
    const qreal intro = view ? easeOutCubic(view->introProgress(index)) : 1.0;
    if (intro <= 0.0) {
        return;
    }
    const bool selected = opt.state & QStyle::State_Selected;
    const bool enabled = opt.state & QStyle::State_Enabled;
    auto instance = (BaseInstance*)index.data(InstanceList::InstancePointerRole).value<void*>();
    const bool running = instance && instance->isRunning();
    const QColor tone = running ? gold : accent;

    painter->save();
    painter->setRenderHint(QPainter::Antialiasing, true);
    painter->setRenderHint(QPainter::SmoothPixmapTransform, true);
    painter->setOpacity(intro);

    // a flat block card: it fades and slides in, and lifts a little while hovered; no tilt, glow or shine
    QRectF card = QRectF(opt.rect).adjusted(2, kLift + 1, -2, -1);
    card.translate(0, (1.0 - intro) * 18.0 - hover * 3.0);
    const QPointF center = card.center();

    painter->setPen(Qt::NoPen);
    painter->setBrush(mix(raised, raisedHi, qMax(hover, selected ? 1.0 : 0.0)));
    painter->drawPath(rounded(card, kRadius));
    painter->setRenderHint(QPainter::Antialiasing, false);
    blockEdges(*painter, card, kRadius, 2.0, 0.8);
    if (selected || running) {
        // a frame in the accent (gold while playing), inside the card so the grid stays calm
        const qreal w = 2.0;
        QPainterPath frame = rounded(card, kRadius) - rounded(card.adjusted(w, w, -w, -w), kRadius - w);
        painter->setBrush(tone);
        painter->drawPath(frame);
    }

    QIcon::Mode mode = enabled ? QIcon::Normal : QIcon::Disabled;
    QIcon::State state = opt.state & QStyle::State_Open ? QIcon::On : QIcon::Off;

    // the icon sits in a slot, like an item in an inventory: sunk in, dark on top, light at the bottom
    {
        const qreal pad = 8.0;
        const QRectF slot(center.x() - kIconSize / 2.0 - pad, card.top() + kPadTop - pad, kIconSize + pad * 2, kIconSize + pad * 2);
        painter->setBrush(mix(ink, selected ? tone : ink, 0.12));
        painter->drawRect(slot);
        painter->fillRect(QRectF(slot.left(), slot.top(), slot.width(), 2), withAlpha(Qt::black, 0.45));
        painter->fillRect(QRectF(slot.left(), slot.top(), 2, slot.height()), withAlpha(Qt::black, 0.45));
        painter->fillRect(QRectF(slot.left(), slot.bottom() - 2, slot.width(), 2), withAlpha(Qt::white, 0.10));
        painter->fillRect(QRectF(slot.right() - 2, slot.top(), 2, slot.height()), withAlpha(Qt::white, 0.10));
        painter->setRenderHint(QPainter::Antialiasing, true);
        const QRectF iconBox(center.x() - kIconSize / 2.0, card.top() + kPadTop, kIconSize, kIconSize);
        opt.icon.paint(painter, iconBox.toRect(), Qt::AlignCenter, mode, state);
    }

    // name
    QRectF textRect = card.adjusted(kTextInset, kPadTop + kIconSize + kGap, -kTextInset, -(kPadBottom + kMetaHeight));
    {
        QFont font = cardFont(opt);
        painter->setFont(font);
        painter->setPen(enabled ? (selected || hover > 0.5 ? text : mix(muted, text, 0.6)) : faint);
        QTextOption textOption;
        textOption.setWrapMode(QTextOption::WrapAtWordBoundaryOrAnywhere);
        textOption.setTextDirection(opt.direction);
        textOption.setAlignment(Qt::AlignHCenter);
        QTextLayout textLayout;
        textLayout.setTextOption(textOption);
        textLayout.setFont(font);
        textLayout.setText(opt.text);
        qreal width, height;
        viewItemTextLayout(textLayout, textRect.width(), height, width);
        for (int i = 0; i < textLayout.lineCount(); ++i) {
            textLayout.lineAt(i).draw(painter, textRect.topLeft());
        }
    }

    // meta line: running state or when it was last played
    if (instance) {
        QString meta;
        QColor metaColor = faint;
        if (running) {
            meta = QObject::tr("Playing");
            metaColor = gold;
        } else if (instance->hasCrashed() || instance->hasVersionBroken()) {
            meta = QObject::tr("Needs attention");
            metaColor = danger;
        } else if (instance->lastLaunch() > 0) {
            meta = Time::relativeAgo(instance->lastLaunch());
        } else {
            meta = QObject::tr("Never played");
        }
        QFont metaFont = opt.font;
        metaFont.setPointSizeF(metaFont.pointSizeF() - 1);
        painter->setFont(metaFont);
        painter->setPen(mix(metaColor, metaColor.lighter(130), hover));
        painter->drawText(QRectF(card.left() + 6, card.bottom() - kPadBottom - kMetaHeight + 2, card.width() - 12, kMetaHeight),
                          Qt::AlignHCenter | Qt::AlignVCenter, meta);

        if (running) {
            drawStatusPixel(painter, QPointF(card.right() - 15, card.top() + 15), gold, view ? view->pulsePhase() : 0.0);
        } else if (instance->hasCrashed() || instance->hasVersionBroken()) {
            drawStatusPixel(painter, QPointF(card.right() - 15, card.top() + 15), danger, 0.0);
        }
    }

    painter->restore();
}

QSize ListViewDelegate::sizeHint(const QStyleOptionViewItem& option, const QModelIndex& index) const
{
    QStyleOptionViewItem opt = option;
    initStyleOption(&opt, index);
    opt.text = index.data().toString();
    const QSize text = viewItemTextSize(&opt);
    return QSize(InstanceView::kCardWidth, kLift + kPadTop + kIconSize + kGap + text.height() + kMetaHeight + kPadBottom + 4);
}

class NoReturnTextEdit : public QTextEdit {
    Q_OBJECT
   public:
    explicit NoReturnTextEdit(QWidget* parent) : QTextEdit(parent)
    {
        setTextInteractionFlags(Qt::TextEditorInteraction);
        setHorizontalScrollBarPolicy(Qt::ScrollBarPolicy::ScrollBarAlwaysOff);
        setVerticalScrollBarPolicy(Qt::ScrollBarPolicy::ScrollBarAlwaysOff);
    }
    bool event(QEvent* event) override
    {
        auto eventType = event->type();
        if (eventType == QEvent::KeyPress || eventType == QEvent::KeyRelease) {
            QKeyEvent* keyEvent = static_cast<QKeyEvent*>(event);
            auto key = keyEvent->key();
            if ((key == Qt::Key_Return || key == Qt::Key_Enter) && eventType == QEvent::KeyPress) {
                emit editingDone();
                return true;
            }
            if (key == Qt::Key_Tab) {
                return true;
            }
        }
        return QTextEdit::event(event);
    }
   signals:
    void editingDone();
};

void ListViewDelegate::updateEditorGeometry(QWidget* editor,
                                            const QStyleOptionViewItem& option,
                                            [[maybe_unused]] const QModelIndex& index) const
{
    QRect textRect = option.rect;
    textRect.adjust(kTextInset / 2, kLift + kPadTop + kIconSize + kGap / 2, -kTextInset / 2, -2);
    editor->setGeometry(textRect);
}

void ListViewDelegate::setEditorData(QWidget* editor, const QModelIndex& index) const
{
    auto text = index.data(Qt::EditRole).toString();
    QTextEdit* realEditor = qobject_cast<NoReturnTextEdit*>(editor);
    realEditor->setAlignment(Qt::AlignHCenter | Qt::AlignTop);
    realEditor->append(text);
    realEditor->selectAll();
    realEditor->document()->clearUndoRedoStacks();
}

void ListViewDelegate::setModelData(QWidget* editor, QAbstractItemModel* model, const QModelIndex& index) const
{
    QTextEdit* realEditor = qobject_cast<NoReturnTextEdit*>(editor);
    QString text = realEditor->toPlainText();
    const auto before = model->data(index).toString();
    if (model->setData(index, text)) {
        emit textChanged(before, model->data(index).toString());
    }
}

QWidget* ListViewDelegate::createEditor(QWidget* parent,
                                        [[maybe_unused]] const QStyleOptionViewItem& option,
                                        [[maybe_unused]] const QModelIndex& index) const
{
    auto editor = new NoReturnTextEdit(parent);
    connect(editor, &NoReturnTextEdit::editingDone, this, &ListViewDelegate::editingDone);
    return editor;
}

void ListViewDelegate::editingDone()
{
    NoReturnTextEdit* editor = qobject_cast<NoReturnTextEdit*>(sender());
    emit commitData(editor);
    emit closeEditor(editor);
}

#include "InstanceDelegate.moc"
