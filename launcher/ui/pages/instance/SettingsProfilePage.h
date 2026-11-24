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

#include <QWidget>

#include "ui/pages/BasePage.h"

class MinecraftInstance;
class QComboBox;
class QPushButton;
class QLabel;

// The instance's settings profile (see minecraft/SettingsProfiles.h): its own settings, or one of the shared profiles
// (PvP, Building, ...), with buttons to make, rename and delete profiles.
class SettingsProfilePage : public QWidget, public BasePage {
    Q_OBJECT

   public:
    explicit SettingsProfilePage(MinecraftInstance* instance, QWidget* parent = nullptr);

    QString displayName() const override { return tr("Settings profile"); }
    QIcon icon() const override { return QIcon::fromTheme("settings"); }
    QString id() const override { return "settings-profile"; }
    bool apply() override;
    QString helpPage() const override { return "Instance-settings"; }

   private:
    void refresh(const QString& select);
    void choose();
    void makeNew();
    void renameCurrent();
    void deleteCurrent();

    MinecraftInstance* m_instance;
    QComboBox* m_profiles;
    QPushButton* m_rename;
    QPushButton* m_delete;
    QLabel* m_state;
};
