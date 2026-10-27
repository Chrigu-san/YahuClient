/// SPDX-License-Identifier: GPL-3.0-only
/*
 *  PrismLaucher - Minecraft Launcher
 *  Copyright (C) 2023 Rachel Powers <508861+Ryex@users.noreply.github.com>
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

#include "ProgressDialog.h"
#include <QPoint>
#include "ui_ProgressDialog.h"

#include <QDebug>
#include <QKeyEvent>
#include <QLabel>
#include <QRegularExpression>
#include <limits>

#include "tasks/Task.h"

#include "ui/yahu/YahuStyle.h"

// map a value in a numeric range of an arbitrary type to between 0 and INT_MAX
// for getting the best precision out of the qt progress bar
template <typename T>
std::tuple<int, int> map_int_zero_max(T current, T range_max, T range_min)
    requires(std::is_arithmetic_v<T>)
{
    int int_max = std::numeric_limits<int>::max();

    auto type_range = range_max - range_min;
    if (type_range == 0) {
        return { 0, int_max };
    }
    double percentage = static_cast<double>(current - range_min) / static_cast<double>(type_range);
    int mapped_current = percentage * int_max;

    return { mapped_current, int_max };
}

ProgressDialog::ProgressDialog(QWidget* parent) : QDialog(parent), ui(new Ui::ProgressDialog)
{
    ui->setupUi(this);
    ui->taskProgressScrollArea->setHidden(true);

    // one slim bar and a small line of text, nothing that races past
    ui->globalProgressBar->setTextVisible(false);
    ui->globalProgressBar->setFixedHeight(6);
    m_currentItem = new QLabel(this);
    m_currentItem->setStyleSheet(QStringLiteral("color: %1;").arg(YahuStyle::faint.name()));
    QFont small = m_currentItem->font();
    small.setPointSizeF(small.pointSizeF() - 1);
    m_currentItem->setFont(small);
    m_currentItem->setMinimumWidth(1);
    m_currentItem->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    ui->verticalLayout->insertWidget(ui->verticalLayout->indexOf(ui->globalProgressBar) + 1, m_currentItem);
    m_itemTimer.setSingleShot(true);
    m_itemTimer.setInterval(250);
    connect(&m_itemTimer, &QTimer::timeout, this, [this] {
        m_currentItem->setText(m_currentItem->fontMetrics().elidedText(m_pendingItem, Qt::ElideMiddle, m_currentItem->width()));
    });
    this->setWindowFlags(this->windowFlags() & ~Qt::WindowContextHelpButtonHint);
    setAttribute(Qt::WidgetAttribute::WA_QuitOnClose, true);
    changeProgress(0, 100);
    updateSize(true);
    hideSkipButton();
}

void ProgressDialog::hideSkipButton()
{
    ui->skipButton->setEnabled(false);
    ui->skipButton->setVisible(false);
    updateSize();
}

void ProgressDialog::showSkipButton(const QString& label)
{
    ui->skipButton->setAutoDefault(false);
    ui->skipButton->setDefault(false);
    ui->skipButton->setFocusPolicy(Qt::ClickFocus);
    ui->skipButton->setEnabled(true);
    ui->skipButton->setVisible(true);
    ui->skipButton->setText(label);
    updateSize();
}

void ProgressDialog::on_skipButton_clicked(bool checked)
{
    Q_UNUSED(checked);
    if (ui->skipButton->isEnabled())  // prevent other triggers from aborting
        m_task->abort();
}

ProgressDialog::~ProgressDialog()
{
    for (auto conn : this->m_taskConnections) {
        disconnect(conn);
    }
    delete ui;
}

void ProgressDialog::updateSize(bool recenterParent)
{
    QSize lastSize = this->size();
    QPoint lastPos = this->pos();
    int minHeight = ui->globalStatusDetailsLabel->minimumSize().height() + (ui->verticalLayout->spacing() * 2);
    minHeight += ui->globalProgressBar->minimumSize().height() + ui->verticalLayout->spacing();
    if (!ui->taskProgressScrollArea->isHidden())
        minHeight += ui->taskProgressScrollArea->minimumSizeHint().height() + ui->verticalLayout->spacing();
    if (ui->skipButton->isVisible())
        minHeight += ui->skipButton->height() + ui->verticalLayout->spacing();
    minHeight = std::max(minHeight, 60);
    QSize minSize = QSize(480, minHeight);

    setMinimumSize(minSize);
    adjustSize();

    QSize newSize = this->size();
    // if the current window is a different size
    auto parent = this->parentWidget();
    if (recenterParent && parent) {
        auto newX = std::max(0, parent->x() + ((parent->width() - newSize.width()) / 2));
        auto newY = std::max(0, parent->y() + ((parent->height() - newSize.height()) / 2));
        this->move(newX, newY);
    } else if (lastSize != newSize) {
        // center on old position after resize
        QSize sizeDiff = lastSize - newSize;  // last size was smaller, the results should be negative
        auto newX = std::max(0, lastPos.x() + (sizeDiff.width() / 2));
        auto newY = std::max(0, lastPos.y() + (sizeDiff.height() / 2));
        this->move(newX, newY);
    }
}

int ProgressDialog::execWithTask(Task* task)
{
    this->m_task = task;

    if (!task) {
        qDebug() << "Programmer error: Progress dialog created with null task.";
        return QDialog::DialogCode::Accepted;
    }

    QDialog::DialogCode result{};
    if (handleImmediateResult(result)) {
        return result;
    }

    // Connect signals.
    this->m_taskConnections.push_back(connect(task, &Task::started, this, &ProgressDialog::onTaskStarted));
    this->m_taskConnections.push_back(connect(task, &Task::failed, this, &ProgressDialog::onTaskFailed));
    this->m_taskConnections.push_back(connect(task, &Task::succeeded, this, &ProgressDialog::onTaskSucceeded));
    this->m_taskConnections.push_back(connect(task, &Task::status, this, &ProgressDialog::changeStatus));
    this->m_taskConnections.push_back(connect(task, &Task::details, this, &ProgressDialog::changeStatus));
    this->m_taskConnections.push_back(connect(task, &Task::stepProgress, this, &ProgressDialog::changeStepProgress));
    this->m_taskConnections.push_back(connect(task, &Task::progress, this, &ProgressDialog::changeProgress));
    this->m_taskConnections.push_back(connect(task, &Task::aborted, this, &ProgressDialog::hide));
    this->m_taskConnections.push_back(connect(task, &Task::abortStatusChanged, ui->skipButton, &QPushButton::setEnabled));
    this->m_taskConnections.push_back(connect(task, &Task::abortButtonTextChanged, ui->skipButton, &QPushButton::setText));

    m_is_multi_step = task->isMultiStep();
    updateSize();

    // It's a good idea to start the task after we entered the dialog's event loop :^)
    if (!task->isRunning()) {
        QMetaObject::invokeMethod(task, &Task::start, Qt::QueuedConnection);
    } else {
        changeStatus(task->getStatus());
        changeProgress(task->getProgress(), task->getTotalProgress());
    }

    return QDialog::exec();
}

// TODO: only provide the unique_ptr overloads
int ProgressDialog::execWithTask(std::unique_ptr<Task>&& task)
{
    connect(this, &ProgressDialog::destroyed, task.get(), &Task::deleteLater);
    return execWithTask(task.release());
}
int ProgressDialog::execWithTask(std::unique_ptr<Task>& task)
{
    connect(this, &ProgressDialog::destroyed, task.get(), &Task::deleteLater);
    return execWithTask(task.release());
}

bool ProgressDialog::handleImmediateResult(QDialog::DialogCode& result)
{
    if (m_task->isFinished()) {
        if (m_task->wasSuccessful()) {
            result = QDialog::Accepted;
        } else {
            result = QDialog::Rejected;
        }
        return true;
    }
    return false;
}

Task* ProgressDialog::getTask()
{
    return m_task;
}

void ProgressDialog::onTaskStarted() {}

void ProgressDialog::onTaskFailed([[maybe_unused]] QString failure)
{
    reject();
    hide();
}

void ProgressDialog::onTaskSucceeded()
{
    accept();
    hide();
}

void ProgressDialog::changeStatus([[maybe_unused]] const QString& status)
{
    // "Executing task 3 out of 5" -> "Step 3 of 5"
    static const QRegularExpression steps(QStringLiteral(R"(^Executing task (\d+) out of (\d+)$)"));
    QString statusText = m_task->getStatus();
    if (const auto match = steps.match(statusText); match.hasMatch()) {
        statusText = tr("Step %1 of %2").arg(match.captured(1), match.captured(2));
    }
    ui->globalStatusLabel->setText(statusText);
    ui->globalStatusLabel->adjustSize();
    ui->globalStatusDetailsLabel->setText(m_task->getDetails());
    ui->globalStatusDetailsLabel->adjustSize();

    updateSize();
}

void ProgressDialog::changeStepProgress(const TaskStepProgress& task_progress)
{
    m_is_multi_step = true;
    if (task_progress.isDone()) {
        return;
    }
    QString item = task_progress.status;
    if (!task_progress.details.isEmpty() && task_progress.details != item) {
        item = item.isEmpty() ? task_progress.details : item + QStringLiteral("  ·  ") + task_progress.details;
    }
    if (item.isEmpty()) {
        return;
    }
    // "Requesting https://host/a/b/file.jar" reads better as "Downloading file.jar"
    static const QRegularExpression url(QStringLiteral(R"(https?://\S*/([^/\s?#]+)\S*)"));
    item.replace(url, QStringLiteral(R"(\1)"));
    item.replace(QRegularExpression(QStringLiteral(R"(^Requesting\b)")), tr("Downloading"));
    m_pendingItem = item;
    if (!m_itemTimer.isActive()) {
        m_itemTimer.start();
    }
}

void ProgressDialog::changeProgress(qint64 current, qint64 total)
{
    ui->globalProgressBar->setMaximum(total);
    ui->globalProgressBar->setValue(current);
}

void ProgressDialog::keyPressEvent(QKeyEvent* e)
{
    if (ui->skipButton->isVisible()) {
        if (e->key() == Qt::Key_Escape) {
            on_skipButton_clicked(true);
            return;
        } else if (e->key() == Qt::Key_Tab) {
            ui->skipButton->setFocusPolicy(Qt::StrongFocus);
            ui->skipButton->setFocus();
            ui->skipButton->setAutoDefault(true);
            ui->skipButton->setDefault(true);
            return;
        }
    }
    QDialog::keyPressEvent(e);
}

void ProgressDialog::closeEvent(QCloseEvent* e)
{
    if (m_task && m_task->isRunning()) {
        e->ignore();
    } else {
        QDialog::closeEvent(e);
    }
}
