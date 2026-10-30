// SPDX-License-Identifier: GPL-3.0-only
/*
 *  Prism Launcher - Minecraft Launcher
 *  Copyright (C) 2022 Sefa Eyeoglu <contact@scrumplex.net>
 *  Copyright (C) 2023 TheKodeToad <TheKodeToad@proton.me>
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

#include "VersionListView.h"
#include "ui/yahu/YahuStyle.h"
#include <QApplication>
#include <QDrag>
#include <QHeaderView>
#include <QMouseEvent>
#include <QPainter>

VersionListView::VersionListView(QWidget* parent) : QTreeView(parent)
{
    m_emptyString = tr("No versions are currently available.");
    setVerticalScrollMode(QAbstractItemView::ScrollPerPixel);
}

void VersionListView::rowsInserted(const QModelIndex& parent, int start, int end)
{
    m_itemCount += end - start + 1;
    updateEmptyViewPort();
    QTreeView::rowsInserted(parent, start, end);
}

void VersionListView::rowsAboutToBeRemoved(const QModelIndex& parent, int start, int end)
{
    m_itemCount -= end - start + 1;
    updateEmptyViewPort();
    QTreeView::rowsAboutToBeRemoved(parent, start, end);
}

void VersionListView::setModel(QAbstractItemModel* model)
{
    m_itemCount = model->rowCount();
    updateEmptyViewPort();
    QTreeView::setModel(model);
}

void VersionListView::reset()
{
    if (model()) {
        m_itemCount = model()->rowCount();
    } else {
        m_itemCount = 0;
    }
    updateEmptyViewPort();
    QTreeView::reset();
}

void VersionListView::setEmptyString(QString emptyString)
{
    m_emptyString = emptyString;
    updateEmptyViewPort();
}

void VersionListView::setEmptyErrorString(QString emptyErrorString)
{
    m_emptyErrorString = emptyErrorString;
    updateEmptyViewPort();
}

void VersionListView::setEmptyMode(VersionListView::EmptyMode mode)
{
    m_emptyMode = mode;
    updateEmptyViewPort();
}

void VersionListView::updateEmptyViewPort()
{
#ifndef QT_NO_ACCESSIBILITY
    setAccessibleDescription(currentEmptyString());
#endif /* !QT_NO_ACCESSIBILITY */

    if (!m_itemCount) {
        viewport()->update();
    }
}

void VersionListView::paintEvent(QPaintEvent* event)
{
    if (m_itemCount) {
        QTreeView::paintEvent(event);
    } else {
        paintInfoLabel(event);
    }
}

QString VersionListView::currentEmptyString() const
{
    switch (m_emptyMode) {
        default:
        case VersionListView::String:
            return m_emptyString;
        case VersionListView::ErrorString:
            return m_emptyErrorString;
    }
}

void VersionListView::paintInfoLabel(QPaintEvent* event) const
{
    QString emptyString = currentEmptyString();

    QPainter painter(viewport());
    painter.setRenderHint(QPainter::Antialiasing, true);
    QFont font = YahuStyle::pixelFont(8);

    QRect bounds = viewport()->geometry();
    bounds.moveTop(0);
    auto innerBounds = bounds.adjusted(16, 16, -16, -16);

    painter.setFont(font);
    auto textRect = painter.fontMetrics().boundingRect(innerBounds, Qt::AlignHCenter | Qt::TextWordWrap, emptyString);
    textRect.moveCenter(bounds.center());
    if (!event->rect().intersects(textRect.adjusted(-30, -30, 30, 30))) {
        return;
    }

    // a dashed pixel frame with the message in the pixel font
    const QRectF frame = QRectF(textRect).adjusted(-18, -14, 18, 14);
    QPen dash(YahuStyle::lineHi, 1.0, Qt::CustomDashLine);
    dash.setDashPattern({ 3, 3 });
    painter.setPen(dash);
    painter.setBrush(YahuStyle::withAlpha(YahuStyle::ink, 0.6));
    painter.drawPath(YahuStyle::rounded(frame, YahuStyle::kRadiusCard));
    painter.setPen(m_emptyMode == ErrorString ? YahuStyle::danger : YahuStyle::muted);
    painter.drawText(textRect, Qt::AlignHCenter | Qt::TextWordWrap, emptyString);
}
