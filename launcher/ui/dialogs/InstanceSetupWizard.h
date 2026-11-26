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

#include <QDialog>
#include <QStringList>

class MinecraftInstance;
class QComboBox;
class QLabel;
class QPushButton;
class QStackedWidget;
class ResourceFolderModel;

// The steps after a new instance exists: mods, resource packs, shaders, settings profile. Each browse step opens the
// download library (Modrinth) of that kind, installs what you pick, and can be skipped.
class InstanceSetupWizard : public QDialog {
    Q_OBJECT

   public:
    enum Step { Mods, ResourcePacks, Shaders, Profile, StepCount };

    InstanceSetupWizard(MinecraftInstance* instance, QWidget* parent = nullptr);

    // The wizard only makes sense for an instance made from scratch (not for a modpack, which brings its own mods).
    static bool worthShowing(MinecraftInstance* instance);

   private slots:
    void next();
    void back();
    void browse();
    void manageProfiles();

   private:
    QWidget* browsePage(Step step, const QString& title, const QString& text, const QString& button);
    QWidget* profilePage();
    void go(int index);
    void refreshProfiles(const QString& select);
    void updateButtons();
    void finish();
    bool modsPossible() const;

    MinecraftInstance* m_instance;
    QStackedWidget* m_pages = nullptr;
    QLabel* m_steps = nullptr;
    QPushButton* m_back = nullptr;
    QPushButton* m_next = nullptr;
    QComboBox* m_profiles = nullptr;
    QLabel* m_added[StepCount];
    QStringList m_addedNames[StepCount];
};
