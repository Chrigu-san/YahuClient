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
constexpr qreal kCut = 12.0;
constexpr qreal kMaxTilt = 9.0;

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
    painter->drawRect(QRectF(center.x() - 3.5, center.y() - 3.5, 7, 7));
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

    QRectF card = QRectF(opt.rect).adjusted(2, kLift + 1, -2, -1);
    const QPointF center = card.center();

    // tilt the card towards the cursor while hovered, like holding it in your hand
    QPointF rel(0, 0);
    if (view && hover > 0.0) {
        const QPointF cursor = view->cursorPosition();
        rel = QPointF(qBound(-1.0, (cursor.x() - center.x()) / (card.width() / 2), 1.0),
                      qBound(-1.0, (cursor.y() - center.y()) / (card.height() / 2), 1.0));
    }
    QTransform tilt;
    tilt.translate(center.x(), center.y() + (1.0 - intro) * 18.0 - hover * kLift);
    tilt.rotate(rel.x() * kMaxTilt * hover, Qt::YAxis);
    tilt.rotate(-rel.y() * kMaxTilt * hover, Qt::XAxis);
    const qreal scale = 0.92 + 0.08 * intro + 0.03 * hover;
    tilt.scale(scale, scale);
    tilt.translate(-center.x(), -center.y());
    painter->setTransform(tilt, true);

    const QPainterPath shape = chamfer(card, kCut);

    // drop shadow + accent glow
    if (hover > 0.01 || selected || running) {
        const qreal strength = qMax(qMax(hover, selected ? 0.6 : 0.0), running ? 0.7 : 0.0);
        painter->setPen(Qt::NoPen);
        for (int i = 5; i >= 1; --i) {
            painter->setBrush(withAlpha(tone, 0.03 * strength * (6 - i)));
            painter->drawPath(chamfer(card.adjusted(-i * 1.6, -i * 1.2 + 4, i * 1.6, i * 2.4 + 4), kCut + i));
        }
    }

    QColor fill = mix(withAlpha(raised, 0.88), withAlpha(raisedHi, 0.96), hover);
    if (selected) {
        fill = mix(fill, withAlpha(tone, 0.95), 0.12);
    }
    painter->setPen(Qt::NoPen);
    painter->setBrush(fill);
    painter->drawPath(shape);

    // light reflection that follows the cursor across the card
    if (hover > 0.01) {
        painter->save();
        painter->setClipPath(shape);
        const QPointF spot = center + QPointF(rel.x() * card.width() * 0.5, rel.y() * card.height() * 0.5);
        QRadialGradient sheen(spot, card.width() * 0.8);
        sheen.setColorAt(0.0, QColor(255, 255, 255, int(34 * hover)));
        sheen.setColorAt(1.0, QColor(255, 255, 255, 0));
        painter->setBrush(sheen);
        painter->drawRect(card);
        painter->restore();
    }

    // border, with lit corner brackets when hovered or selected
    painter->setBrush(Qt::NoBrush);
    painter->setPen(QPen(selected ? tone : mix(line, lineHi, hover), selected ? 1.5 : 1.0));
    painter->drawPath(shape);
    const qreal bracket = qMax(hover, selected ? 1.0 : 0.0);
    if (bracket > 0.01) {
        painter->setPen(QPen(withAlpha(tone, bracket), 2.0, Qt::SolidLine, Qt::SquareCap, Qt::MiterJoin));
        const qreal len = 10 + 8 * bracket;
        const QRectF b = card.adjusted(-3, -3, 3, 3);
        painter->drawPolyline(QPolygonF() << QPointF(b.left(), b.top() + kCut + len) << QPointF(b.left(), b.top() + kCut)
                                          << QPointF(b.left() + kCut, b.top()) << QPointF(b.left() + kCut + len, b.top()));
        painter->drawPolyline(QPolygonF() << QPointF(b.right(), b.bottom() - kCut - len) << QPointF(b.right(), b.bottom() - kCut)
                                          << QPointF(b.right() - kCut, b.bottom()) << QPointF(b.right() - kCut - len, b.bottom()));
    }

    QIcon::Mode mode = enabled ? QIcon::Normal : QIcon::Disabled;
    QIcon::State state = opt.state & QStyle::State_Open ? QIcon::On : QIcon::Off;

    // icon on a little pixel pedestal, bobbing up on hover
    {
        const qreal size = kIconSize * (1.0 + 0.1 * hover);
        const qreal top = card.top() + kPadTop - hover * 3.0;
        QRectF iconBox(center.x() - size / 2.0, top + (kIconSize - size) / 2.0, size, size);
        painter->setPen(Qt::NoPen);
        painter->setBrush(withAlpha(Qt::black, 0.25 + 0.15 * hover));
        painter->drawRect(QRectF(center.x() - kIconSize * 0.36, card.top() + kPadTop + kIconSize + 3, kIconSize * 0.72, 3));
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

    // meta line in the pixel font: running state or time played
    if (instance) {
        QString meta;
        QColor metaColor = faint;
        if (running) {
            meta = QObject::tr("PLAYING");
            metaColor = gold;
        } else if (instance->hasCrashed() || instance->hasVersionBroken()) {
            meta = QObject::tr("NEEDS ATTENTION");
            metaColor = danger;
        } else if (instance->totalTimePlayed() > 0) {
            meta = Time::prettifyDuration(instance->totalTimePlayed(), true).toUpper();
        } else {
            meta = QObject::tr("NEW");
            metaColor = mix(faint, accent, 0.6);
        }
        painter->setFont(pixelFont(8));
        painter->setPen(mix(metaColor, metaColor.lighter(130), hover));
        painter->drawText(QRectF(card.left() + 6, card.bottom() - kPadBottom - kMetaHeight + 4, card.width() - 12, kMetaHeight),
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
