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

class QListWidget;
class QPushButton;
class QLabel;

// Manages the settings profiles: make one from an instance, import and export profile files, rename, delete.
class SettingsProfilesDialog : public QDialog {
    Q_OBJECT

   public:
    explicit SettingsProfilesDialog(QWidget* parent = nullptr);

   private slots:
    void fromInstance();
    void importFile();
    void exportFile();
    void renameCurrent();
    void deleteCurrent();
    void updateButtons();

   private:
    void refresh(const QString& select = QString());
    QString current() const;
    int usedBy(const QString& name) const;

    QListWidget* m_list = nullptr;
    QLabel* m_info = nullptr;
    QPushButton* m_export = nullptr;
    QPushButton* m_rename = nullptr;
    QPushButton* m_delete = nullptr;
};
