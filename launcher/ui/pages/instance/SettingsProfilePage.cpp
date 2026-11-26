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

#include "SettingsProfilePage.h"

#include "ui/dialogs/SettingsProfilesDialog.h"

#include <QComboBox>
#include <QHBoxLayout>
#include <QInputDialog>
#include <QLabel>
#include <QMessageBox>
#include <QPushButton>
#include <QVBoxLayout>

#include "minecraft/MinecraftInstance.h"
#include "minecraft/SettingsProfiles.h"
#include "settings/SettingsObject.h"

SettingsProfilePage::SettingsProfilePage(MinecraftInstance* instance, QWidget* parent) : QWidget(parent), m_instance(instance)
{
    auto* layout = new QVBoxLayout(this);
    layout->setContentsMargins(16, 16, 16, 16);
    layout->setSpacing(10);

    auto* title = new QLabel(tr("<b>Settings profile</b>"), this);
    layout->addWidget(title);

    auto* about = new QLabel(tr("Share your settings between instances: make profiles like <i>PvP</i> or <i>Building</i> and pick one "
                                "here. Every instance on the same profile gets the same Minecraft settings, key bindings and Yahu "
                                "mod settings (crosshair, lyrics, sky, inventory, menu). What you change in the game is kept in the "
                                "profile when the game closes."),
                             this);
    about->setWordWrap(true);
    layout->addWidget(about);

    m_profiles = new QComboBox(this);
    layout->addWidget(m_profiles);

    auto* buttons = new QHBoxLayout();
    auto* make = new QPushButton(tr("New profile..."), this);
    m_rename = new QPushButton(tr("Rename..."), this);
    m_delete = new QPushButton(tr("Delete"), this);
    buttons->addWidget(make);
    buttons->addWidget(m_rename);
    buttons->addWidget(m_delete);
    auto* manage = new QPushButton(tr("Import / export..."), this);
    buttons->addWidget(manage);
    buttons->addStretch(1);
    connect(manage, &QPushButton::clicked, this, [this] {
        SettingsProfilesDialog(this).exec();
        refresh(m_profiles->currentData().toString());
    });
    layout->addLayout(buttons);

    m_state = new QLabel(this);
    m_state->setWordWrap(true);
    layout->addWidget(m_state);
    layout->addStretch(1);

    connect(make, &QPushButton::clicked, this, &SettingsProfilePage::makeNew);
    connect(m_rename, &QPushButton::clicked, this, &SettingsProfilePage::renameCurrent);
    connect(m_delete, &QPushButton::clicked, this, &SettingsProfilePage::deleteCurrent);

    refresh(m_instance->settings()->get(SettingsProfiles::kSetting).toString());
    connect(m_profiles, &QComboBox::currentIndexChanged, this, &SettingsProfilePage::choose);
}

void SettingsProfilePage::refresh(const QString& select)
{
    const QSignalBlocker block(m_profiles);
    m_profiles->clear();
    m_profiles->addItem(tr("Own settings (only this instance)"), QString());
    for (const QString& name : SettingsProfiles::names()) {
        m_profiles->addItem(name, name);
    }
    const int index = m_profiles->findData(select);
    m_profiles->setCurrentIndex(index < 0 ? 0 : index);
    const bool profile = m_profiles->currentIndex() > 0;
    m_rename->setEnabled(profile);
    m_delete->setEnabled(profile);
    m_state->setText(profile ? tr("This instance uses \"%1\". It takes the profile's settings when it starts.").arg(select)
                             : tr("This instance keeps its own settings."));
}

void SettingsProfilePage::choose()
{
    const QString name = m_profiles->currentData().toString();
    m_instance->settings()->set(SettingsProfiles::kSetting, name);
    refresh(name);
}

void SettingsProfilePage::makeNew()
{
    bool ok = false;
    const QString name = QInputDialog::getText(this, tr("New settings profile"),
                                               tr("Name, for example PvP or Building.\nIt starts from this instance's settings."),
                                               QLineEdit::Normal, QString(), &ok);
    if (!ok) {
        return;
    }
    const QString clean = SettingsProfiles::cleanName(name);
    if (!SettingsProfiles::create(clean, m_instance->gameRoot())) {
        QMessageBox::warning(this, tr("New settings profile"),
                             clean.isEmpty() ? tr("Please give the profile a name.") : tr("There is already a profile called \"%1\".").arg(clean));
        return;
    }
    m_instance->settings()->set(SettingsProfiles::kSetting, clean);
    refresh(clean);
}

void SettingsProfilePage::renameCurrent()
{
    const QString current = m_profiles->currentData().toString();
    if (current.isEmpty()) {
        return;
    }
    bool ok = false;
    const QString name = QInputDialog::getText(this, tr("Rename settings profile"), tr("New name for \"%1\":").arg(current),
                                               QLineEdit::Normal, current, &ok);
    if (!ok) {
        return;
    }
    const QString clean = SettingsProfiles::cleanName(name);
    if (!SettingsProfiles::rename(current, clean)) {
        QMessageBox::warning(this, tr("Rename settings profile"),
                             clean.isEmpty() ? tr("Please give the profile a name.") : tr("There is already a profile called \"%1\".").arg(clean));
        return;
    }
    refresh(clean);
}

void SettingsProfilePage::deleteCurrent()
{
    const QString current = m_profiles->currentData().toString();
    if (current.isEmpty()) {
        return;
    }
    const auto answer = QMessageBox::question(this, tr("Delete settings profile"),
                                              tr("Delete \"%1\"? Instances that use it keep the settings they have now, as their own.")
                                                  .arg(current));
    if (answer != QMessageBox::Yes) {
        return;
    }
    SettingsProfiles::remove(current);
    refresh(QString());
}

bool SettingsProfilePage::apply()
{
    m_instance->settings()->set(SettingsProfiles::kSetting, m_profiles->currentData().toString());
    return true;
}
