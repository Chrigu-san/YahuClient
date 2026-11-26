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

#include "SettingsProfilesDialog.h"

#include <QDir>
#include <QFileDialog>
#include <QFileInfo>
#include <QHBoxLayout>
#include <QInputDialog>
#include <QLabel>
#include <QLineEdit>
#include <QListWidget>
#include <QMessageBox>
#include <QPushButton>
#include <QVBoxLayout>

#include "Application.h"
#include "InstanceList.h"
#include "minecraft/MinecraftInstance.h"
#include "minecraft/SettingsProfiles.h"
#include "settings/SettingsObject.h"

namespace {
const QString kFileFilter = QStringLiteral("Yahu settings profile (*.yahuprofile);;Zip (*.zip)");
}

SettingsProfilesDialog::SettingsProfilesDialog(QWidget* parent) : QDialog(parent)
{
    setWindowTitle(tr("Settings profiles"));
    resize(560, 380);

    auto* layout = new QVBoxLayout(this);
    layout->setSpacing(10);

    auto* about = new QLabel(tr("A profile holds your Minecraft settings, key bindings and Yahu mod settings, for example "
                                "<i>PvP</i> or <i>Building</i>. Any instance can use one (right-click it, Edit, Settings profile) "
                                "and you can pick one when you make a new instance."),
                             this);
    about->setWordWrap(true);
    layout->addWidget(about);

    auto* row = new QHBoxLayout();
    m_list = new QListWidget(this);
    row->addWidget(m_list, 1);

    auto* column = new QVBoxLayout();
    auto* make = new QPushButton(tr("New from instance..."), this);
    auto* import = new QPushButton(tr("Import file..."), this);
    m_export = new QPushButton(tr("Export file..."), this);
    m_rename = new QPushButton(tr("Rename..."), this);
    m_delete = new QPushButton(tr("Delete"), this);
    for (auto* button : { make, import, m_export, m_rename, m_delete }) {
        column->addWidget(button);
    }
    column->addStretch(1);
    row->addLayout(column);
    layout->addLayout(row, 1);

    m_info = new QLabel(this);
    m_info->setWordWrap(true);
    layout->addWidget(m_info);

    auto* close = new QPushButton(tr("Close"), this);
    auto* bottom = new QHBoxLayout();
    bottom->addStretch(1);
    bottom->addWidget(close);
    layout->addLayout(bottom);

    connect(make, &QPushButton::clicked, this, &SettingsProfilesDialog::fromInstance);
    connect(import, &QPushButton::clicked, this, &SettingsProfilesDialog::importFile);
    connect(m_export, &QPushButton::clicked, this, &SettingsProfilesDialog::exportFile);
    connect(m_rename, &QPushButton::clicked, this, &SettingsProfilesDialog::renameCurrent);
    connect(m_delete, &QPushButton::clicked, this, &SettingsProfilesDialog::deleteCurrent);
    connect(close, &QPushButton::clicked, this, &QDialog::accept);
    connect(m_list, &QListWidget::currentRowChanged, this, &SettingsProfilesDialog::updateButtons);

    refresh();
}

QString SettingsProfilesDialog::current() const
{
    auto* item = m_list->currentItem();
    return item ? item->data(Qt::UserRole).toString() : QString();
}

int SettingsProfilesDialog::usedBy(const QString& name) const
{
    int count = 0;
    auto instances = APPLICATION->instances();
    for (int i = 0; i < instances->count(); i++) {
        if (instances->at(i)->settings()->get(SettingsProfiles::kSetting).toString() == name) {
            count++;
        }
    }
    return count;
}

void SettingsProfilesDialog::refresh(const QString& select)
{
    m_list->clear();
    for (const QString& name : SettingsProfiles::names()) {
        const int used = usedBy(name);
        auto* item = new QListWidgetItem(used > 0 ? tr("%1  (used by %n instance(s))", nullptr, used).arg(name) : name, m_list);
        item->setData(Qt::UserRole, name);
        if (name == select) {
            m_list->setCurrentItem(item);
        }
    }
    if (!m_list->currentItem() && m_list->count() > 0) {
        m_list->setCurrentRow(0);
    }
    updateButtons();
}

void SettingsProfilesDialog::updateButtons()
{
    const bool any = !current().isEmpty();
    m_export->setEnabled(any);
    m_rename->setEnabled(any);
    m_delete->setEnabled(any);
    m_info->setText(m_list->count() == 0 ? tr("No profiles yet. Make one from an instance you already set up, or import a file.")
                                         : QString());
}

void SettingsProfilesDialog::fromInstance()
{
    auto instances = APPLICATION->instances();
    QStringList labels;
    QList<MinecraftInstance*> list;
    for (int i = 0; i < instances->count(); i++) {
        if (auto* mc = dynamic_cast<MinecraftInstance*>(instances->at(i))) {
            labels << mc->name();
            list << mc;
        }
    }
    if (list.isEmpty()) {
        QMessageBox::information(this, tr("New from instance"), tr("There are no instances to take settings from yet."));
        return;
    }
    bool ok = false;
    const QString picked = QInputDialog::getItem(this, tr("New from instance"), tr("Take the settings of this instance:"), labels, 0,
                                                 false, &ok);
    if (!ok) {
        return;
    }
    auto* source = list.at(labels.indexOf(picked));
    if (!SettingsProfiles::hasAny(source->gameRoot())) {
        QMessageBox::information(this, tr("New from instance"),
                                 tr("\"%1\" has no saved settings yet. Start it once and change something, then try again.").arg(picked));
        return;
    }
    const QString name = QInputDialog::getText(this, tr("New settings profile"), tr("Name, for example PvP or Building:"),
                                               QLineEdit::Normal, picked, &ok);
    if (!ok) {
        return;
    }
    const QString clean = SettingsProfiles::cleanName(name);
    if (!SettingsProfiles::create(clean, source->gameRoot())) {
        QMessageBox::warning(this, tr("New settings profile"),
                             clean.isEmpty() ? tr("Please give the profile a name.")
                                             : tr("There is already a profile called \"%1\".").arg(clean));
        return;
    }
    refresh(clean);
}

void SettingsProfilesDialog::importFile()
{
    const QString file = QFileDialog::getOpenFileName(this, tr("Import settings profile"), QString(), kFileFilter);
    if (file.isEmpty()) {
        return;
    }
    QString name = SettingsProfiles::cleanName(QFileInfo(file).completeBaseName());
    bool ok = true;
    name = QInputDialog::getText(this, tr("Import settings profile"), tr("Name of the new profile:"), QLineEdit::Normal, name, &ok);
    if (!ok) {
        return;
    }
    QString error;
    const QString made = SettingsProfiles::importFrom(file, name, &error);
    if (made.isEmpty()) {
        QMessageBox::warning(this, tr("Import settings profile"), error);
        return;
    }
    refresh(made);
}

void SettingsProfilesDialog::exportFile()
{
    const QString name = current();
    if (name.isEmpty()) {
        return;
    }
    QString file = QFileDialog::getSaveFileName(this, tr("Export settings profile"), name + QStringLiteral(".yahuprofile"), kFileFilter);
    if (file.isEmpty()) {
        return;
    }
    if (QFileInfo(file).suffix().isEmpty()) {
        file += QStringLiteral(".yahuprofile");
    }
    const QString error = SettingsProfiles::exportTo(name, file);
    if (!error.isEmpty()) {
        QMessageBox::warning(this, tr("Export settings profile"), error);
        return;
    }
    m_info->setText(tr("Saved \"%1\" to %2.").arg(name, QDir::toNativeSeparators(file)));
}

void SettingsProfilesDialog::renameCurrent()
{
    const QString name = current();
    if (name.isEmpty()) {
        return;
    }
    bool ok = false;
    const QString input = QInputDialog::getText(this, tr("Rename settings profile"), tr("New name for \"%1\":").arg(name),
                                                QLineEdit::Normal, name, &ok);
    if (!ok) {
        return;
    }
    const QString clean = SettingsProfiles::cleanName(input);
    if (!SettingsProfiles::rename(name, clean)) {
        QMessageBox::warning(this, tr("Rename settings profile"),
                             clean.isEmpty() ? tr("Please give the profile a name.")
                                             : tr("There is already a profile called \"%1\".").arg(clean));
        return;
    }
    refresh(clean);
}

void SettingsProfilesDialog::deleteCurrent()
{
    const QString name = current();
    if (name.isEmpty()) {
        return;
    }
    const auto answer = QMessageBox::question(
        this, tr("Delete settings profile"),
        tr("Delete \"%1\"? Instances that use it keep the settings they have now, as their own. Export it first if you want a copy.")
            .arg(name));
    if (answer != QMessageBox::Yes) {
        return;
    }
    SettingsProfiles::remove(name);
    refresh();
}
