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

#include "servers/ServerManager.h"

class QCheckBox;
class QComboBox;
class QDialogButtonBox;
class QDoubleSpinBox;
class QLineEdit;
class QSpinBox;

// Creating a new server, or changing the settings of an existing one.
class ServerSettingsDialog : public QDialog {
    Q_OBJECT
   public:
    // With `existing` the dialog edits that server's settings; otherwise it creates a new one.
    ServerSettingsDialog(ServerManager* manager, const ServerConfig* existing, QWidget* parent = nullptr);

    ServerConfig config() const;
    // Whether the signed-in player should become operator (only offered when creating).
    bool makeOperator() const;

   private:
    void fillVersions(const QString& error = QString());
    void validate();

    ServerManager* m_manager;
    bool m_editing;
    ServerConfig m_base;

    QLineEdit* m_name;
    QComboBox* m_software;
    QComboBox* m_version;
    QCheckBox* m_snapshots;
    QDoubleSpinBox* m_memory;
    QSpinBox* m_port;
    QLineEdit* m_motd;

    QLineEdit* m_levelName;
    QLineEdit* m_seed;
    QComboBox* m_levelType;
    QComboBox* m_gamemode;
    QComboBox* m_difficulty;
    QCheckBox* m_hardcore;
    QCheckBox* m_nether;
    QCheckBox* m_structures;
    QSpinBox* m_spawnProtection;

    QSpinBox* m_maxPlayers;
    QCheckBox* m_onlineMode;
    QCheckBox* m_whitelist;
    QCheckBox* m_pvp;
    QCheckBox* m_flight;
    QCheckBox* m_forceGamemode;
    QSpinBox* m_viewDistance;
    QSpinBox* m_simulationDistance;
    QSpinBox* m_idle;
    QCheckBox* m_operator = nullptr;

    QCheckBox* m_monsters;
    QCheckBox* m_animals;
    QCheckBox* m_villagers;
    QCheckBox* m_commandBlocks;
    QCheckBox* m_secureChat;
    QComboBox* m_java;
    QLineEdit* m_jvmArgs;

    QCheckBox* m_eula = nullptr;
    QDialogButtonBox* m_buttons;
};
