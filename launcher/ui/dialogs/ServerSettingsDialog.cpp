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

#include "ServerSettingsDialog.h"

#include <QCheckBox>
#include <QComboBox>
#include <QDialogButtonBox>
#include <QDoubleSpinBox>
#include <QFormLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QLineEdit>
#include <QPushButton>
#include <QSpinBox>
#include <QTabWidget>
#include <QVBoxLayout>

#include "Application.h"
#include "minecraft/auth/AccountList.h"

namespace {
QLabel* hint(const QString& text)
{
    auto* label = new QLabel(text);
    label->setWordWrap(true);
    label->setProperty("yahuHint", true);
    return label;
}

QComboBox* choice(const QList<QPair<QString, QString>>& items, const QString& current)
{
    auto* box = new QComboBox;
    box->setSizeAdjustPolicy(QComboBox::AdjustToMinimumContentsLengthWithIcon);
    for (const auto& [value, label] : items) {
        box->addItem(label, value);
    }
    box->setCurrentIndex(qMax(0, box->findData(current)));
    return box;
}

QWidget* formPage(QFormLayout*& form)
{
    auto* page = new QWidget;
    form = new QFormLayout(page);
    form->setContentsMargins(18, 18, 18, 12);
    form->setHorizontalSpacing(18);
    form->setVerticalSpacing(10);
    form->setFieldGrowthPolicy(QFormLayout::AllNonFixedFieldsGrow);
    form->setLabelAlignment(Qt::AlignLeft | Qt::AlignVCenter);
    return page;
}
}  // namespace

ServerSettingsDialog::ServerSettingsDialog(ServerManager* manager, const ServerConfig* existing, QWidget* parent)
    : QDialog(parent), m_manager(manager), m_editing(existing != nullptr)
{
    if (existing) {
        m_base = *existing;
    } else {
        m_base.port = manager->suggestPort();
    }
    const ServerConfig& c = m_base;
    setWindowTitle(m_editing ? tr("Server settings") : tr("New server"));
    resize(720, 600);

    auto* tabs = new QTabWidget;
    QFormLayout* form = nullptr;

    // ----- basics
    {
        auto* page = formPage(form);
        m_name = new QLineEdit(c.name);
        m_name->setMaxLength(48);
        form->addRow(tr("Name"), m_name);

        m_software = new QComboBox;
        m_software->setSizeAdjustPolicy(QComboBox::AdjustToMinimumContentsLengthWithIcon);
        m_software->addItem(tr("Paper: fast, supports plugins (recommended)"), int(ServerConfig::Software::Paper));
        m_software->addItem(tr("Vanilla: the official server from Mojang"), int(ServerConfig::Software::Vanilla));
        m_software->addItem(tr("Fabric: for Fabric mods"), int(ServerConfig::Software::Fabric));
        m_software->setCurrentIndex(m_software->findData(int(c.software)));
        form->addRow(tr("Software"), m_software);

        m_version = new QComboBox;
        m_version->setSizeAdjustPolicy(QComboBox::AdjustToMinimumContentsLengthWithIcon);
        m_version->setMaxVisibleItems(16);
        m_snapshots = new QCheckBox(tr("Show snapshots"));
        auto* versionRow = new QHBoxLayout;
        versionRow->addWidget(m_version, 1);
        versionRow->addWidget(m_snapshots);
        form->addRow(tr("Minecraft version"), versionRow);
        if (m_editing) {
            form->addRow(QString(), hint(tr("Changing the software or version downloads it again. Your world stays, but back it up "
                                            "before switching to an older version.")));
        }

        m_memory = new QDoubleSpinBox;
        m_memory->setRange(1.0, 64.0);
        m_memory->setSingleStep(0.5);
        m_memory->setDecimals(1);
        m_memory->setSuffix(tr(" GB"));
        m_memory->setValue(c.memoryMb / 1024.0);
        form->addRow(tr("Memory"), m_memory);
        form->addRow(QString(), hint(tr("4 GB is plenty for a few friends. Modded servers like 6 GB or more.")));

        m_port = new QSpinBox;
        m_port->setRange(1024, 65535);
        m_port->setValue(c.port);
        form->addRow(tr("Port"), m_port);

        m_motd = new QLineEdit(c.motd);
        m_motd->setMaxLength(59);
        form->addRow(tr("Server list message"), m_motd);
        tabs->addTab(page, tr("Basics"));
    }

    // ----- world
    {
        auto* page = formPage(form);
        m_levelName = new QLineEdit(c.levelName);
        form->addRow(tr("World folder"), m_levelName);
        m_seed = new QLineEdit(c.seed);
        m_seed->setPlaceholderText(tr("Random"));
        form->addRow(tr("Seed"), m_seed);
        m_levelType = choice({ { "minecraft:normal", tr("Normal") },
                               { "minecraft:flat", tr("Superflat") },
                               { "minecraft:large_biomes", tr("Large biomes") },
                               { "minecraft:amplified", tr("Amplified") } },
                             c.levelType);
        form->addRow(tr("World type"), m_levelType);
        form->addRow(QString(), hint(tr("Seed and world type only apply when the world is first created.")));
        m_gamemode = choice({ { "survival", tr("Survival") },
                              { "creative", tr("Creative") },
                              { "adventure", tr("Adventure") },
                              { "spectator", tr("Spectator") } },
                            c.gamemode);
        form->addRow(tr("Game mode"), m_gamemode);
        m_difficulty =
            choice({ { "peaceful", tr("Peaceful") }, { "easy", tr("Easy") }, { "normal", tr("Normal") }, { "hard", tr("Hard") } },
                   c.difficulty);
        form->addRow(tr("Difficulty"), m_difficulty);
        m_hardcore = new QCheckBox(tr("Hardcore (one life, then spectator)"));
        m_hardcore->setChecked(c.hardcore);
        m_nether = new QCheckBox(tr("Allow the Nether"));
        m_nether->setChecked(c.allowNether);
        m_structures = new QCheckBox(tr("Generate villages and other structures"));
        m_structures->setChecked(c.generateStructures);
        form->addRow(QString(), m_hardcore);
        form->addRow(QString(), m_nether);
        form->addRow(QString(), m_structures);
        m_spawnProtection = new QSpinBox;
        m_spawnProtection->setRange(0, 128);
        m_spawnProtection->setSuffix(tr(" blocks"));
        m_spawnProtection->setValue(c.spawnProtection);
        form->addRow(tr("Spawn protection"), m_spawnProtection);
        tabs->addTab(page, tr("World"));
    }

    // ----- players
    {
        auto* page = formPage(form);
        m_maxPlayers = new QSpinBox;
        m_maxPlayers->setRange(1, 500);
        m_maxPlayers->setValue(c.maxPlayers);
        form->addRow(tr("Max players"), m_maxPlayers);
        m_onlineMode = new QCheckBox(tr("Only allow real Minecraft accounts"));
        m_onlineMode->setChecked(c.onlineMode);
        m_onlineMode->setToolTip(tr("Turning this off lets anyone join with any name. Keep it on."));
        m_whitelist = new QCheckBox(tr("Whitelist: only players you add can join"));
        m_whitelist->setChecked(c.whitelist);
        m_pvp = new QCheckBox(tr("Players can hurt each other (PvP)"));
        m_pvp->setChecked(c.pvp);
        m_flight = new QCheckBox(tr("Allow flying (needed for some mods)"));
        m_flight->setChecked(c.allowFlight);
        m_forceGamemode = new QCheckBox(tr("Reset players to the default game mode when they join"));
        m_forceGamemode->setChecked(c.forceGamemode);
        for (auto* box : { m_onlineMode, m_whitelist, m_pvp, m_flight, m_forceGamemode }) {
            form->addRow(QString(), box);
        }
        form->addRow(QString(), hint(tr("Add friends to the whitelist from the console: whitelist add <name>")));

        m_viewDistance = new QSpinBox;
        m_viewDistance->setRange(3, 32);
        m_viewDistance->setSuffix(tr(" chunks"));
        m_viewDistance->setValue(c.viewDistance);
        form->addRow(tr("View distance"), m_viewDistance);
        m_simulationDistance = new QSpinBox;
        m_simulationDistance->setRange(3, 32);
        m_simulationDistance->setSuffix(tr(" chunks"));
        m_simulationDistance->setValue(c.simulationDistance);
        form->addRow(tr("Simulation distance"), m_simulationDistance);
        m_idle = new QSpinBox;
        m_idle->setRange(0, 1440);
        m_idle->setSuffix(tr(" min"));
        m_idle->setSpecialValueText(tr("Never"));
        m_idle->setValue(c.idleTimeout);
        form->addRow(tr("Kick idle players after"), m_idle);

        if (!m_editing) {
            const auto account = APPLICATION->accounts()->defaultAccount();
            const QString name = account ? account->profileName() : QString();
            m_operator = new QCheckBox(name.isEmpty() ? tr("Sign in to become operator of your server automatically")
                                                      : tr("Make %1 an operator (admin)").arg(name));
            m_operator->setEnabled(!name.isEmpty());
            m_operator->setChecked(!name.isEmpty());
            form->addRow(QString(), m_operator);
        }
        tabs->addTab(page, tr("Players"));
    }

    // ----- gameplay and advanced
    {
        auto* page = formPage(form);
        m_monsters = new QCheckBox(tr("Monsters"));
        m_monsters->setChecked(c.spawnMonsters);
        m_animals = new QCheckBox(tr("Animals"));
        m_animals->setChecked(c.spawnAnimals);
        m_villagers = new QCheckBox(tr("Villagers"));
        m_villagers->setChecked(c.spawnNpcs);
        auto* spawning = new QHBoxLayout;
        spawning->setSpacing(22);
        spawning->addWidget(m_monsters);
        spawning->addWidget(m_animals);
        spawning->addWidget(m_villagers);
        spawning->addStretch(1);
        form->addRow(tr("Spawn"), spawning);
        m_commandBlocks = new QCheckBox(tr("Enable command blocks"));
        m_commandBlocks->setChecked(c.commandBlocks);
        m_secureChat = new QCheckBox(tr("Require signed chat messages"));
        m_secureChat->setChecked(c.enforceSecureProfile);
        form->addRow(QString(), m_commandBlocks);
        form->addRow(QString(), m_secureChat);

        m_java = new QComboBox;
        // long Java paths must not stretch the whole dialog
        m_java->setSizeAdjustPolicy(QComboBox::AdjustToMinimumContentsLengthWithIcon);
        m_java->setMinimumContentsLength(24);
        m_java->addItem(tr("Automatic (recommended)"), QString());
        for (const auto& java : manager->javas()) {
            m_java->addItem(tr("Java %1  %2").arg(java.major).arg(java.path), java.path);
        }
        if (!c.javaPath.isEmpty() && m_java->findData(c.javaPath) < 0) {
            m_java->addItem(c.javaPath, c.javaPath);
        }
        m_java->setCurrentIndex(qMax(0, m_java->findData(c.javaPath)));
        form->addRow(tr("Java"), m_java);
        m_jvmArgs = new QLineEdit(c.jvmArgs);
        m_jvmArgs->setPlaceholderText(tr("Optional, e.g. -XX:+UseG1GC"));
        form->addRow(tr("Extra Java arguments"), m_jvmArgs);
        tabs->addTab(page, tr("Advanced"));
    }

    auto* layout = new QVBoxLayout(this);
    layout->addWidget(tabs, 1);
    if (!m_editing) {
        m_eula = new QCheckBox(tr("I agree to the Minecraft EULA"));
        auto* eulaRow = new QHBoxLayout;
        eulaRow->addWidget(m_eula);
        auto* link = new QLabel(tr("<a href=\"https://aka.ms/MinecraftEULA\">Read it</a>"));
        link->setOpenExternalLinks(true);
        eulaRow->addWidget(link);
        eulaRow->addStretch(1);
        layout->addLayout(eulaRow);
        connect(m_eula, &QCheckBox::toggled, this, &ServerSettingsDialog::validate);
    }
    m_buttons = new QDialogButtonBox(QDialogButtonBox::Ok | QDialogButtonBox::Cancel);
    m_buttons->button(QDialogButtonBox::Ok)->setText(m_editing ? tr("Save") : tr("Create server"));
    m_buttons->button(QDialogButtonBox::Ok)->setDefault(true);
    layout->addWidget(m_buttons);
    connect(m_buttons, &QDialogButtonBox::accepted, this, &QDialog::accept);
    connect(m_buttons, &QDialogButtonBox::rejected, this, &QDialog::reject);

    connect(m_name, &QLineEdit::textChanged, this, &ServerSettingsDialog::validate);
    connect(m_version, &QComboBox::currentIndexChanged, this, &ServerSettingsDialog::validate);
    connect(m_snapshots, &QCheckBox::toggled, this, [this] { fillVersions(); });
    connect(m_manager, &ServerManager::versionsChanged, this, &ServerSettingsDialog::fillVersions);
    m_snapshots->setChecked(!c.mcVersion.isEmpty() && m_manager->versionsLoaded() && [&] {
        for (const auto& v : m_manager->versions()) {
            if (v.id == c.mcVersion) {
                return !v.release;
            }
        }
        return false;
    }());
    fillVersions();
    m_manager->loadVersions();
}

void ServerSettingsDialog::fillVersions(const QString& error)
{
    const QString current = m_version->currentData().toString().isEmpty() ? m_base.mcVersion : m_version->currentData().toString();
    m_version->clear();
    if (!m_manager->versionsLoaded()) {
        m_version->addItem(error.isEmpty() ? tr("Loading versions…") : tr("Couldn't load versions: %1").arg(error), QString());
        m_version->setEnabled(false);
        if (!m_base.mcVersion.isEmpty()) {
            // editing offline: keep the version we know
            m_version->clear();
            m_version->addItem(m_base.mcVersion, m_base.mcVersion);
            m_version->setEnabled(true);
        }
        validate();
        return;
    }
    m_version->setEnabled(true);
    for (const auto& v : m_manager->versions()) {
        if (v.release || m_snapshots->isChecked() || v.id == current) {
            m_version->addItem(v.release ? v.id : tr("%1 (snapshot)").arg(v.id), v.id);
        }
    }
    const int index = m_version->findData(current);
    m_version->setCurrentIndex(index >= 0 ? index : 0);
    validate();
}

void ServerSettingsDialog::validate()
{
    const bool ok = !m_name->text().trimmed().isEmpty() && !m_version->currentData().toString().isEmpty() &&
                    (!m_eula || m_eula->isChecked());
    m_buttons->button(QDialogButtonBox::Ok)->setEnabled(ok);
}

ServerConfig ServerSettingsDialog::config() const
{
    ServerConfig c = m_base;
    c.name = m_name->text().trimmed();
    c.software = ServerConfig::Software(m_software->currentData().toInt());
    c.mcVersion = m_version->currentData().toString();
    c.memoryMb = int(m_memory->value() * 1024);
    c.port = m_port->value();
    c.motd = m_motd->text();
    c.levelName = m_levelName->text().trimmed().isEmpty() ? QStringLiteral("world") : m_levelName->text().trimmed();
    c.seed = m_seed->text().trimmed();
    c.levelType = m_levelType->currentData().toString();
    c.gamemode = m_gamemode->currentData().toString();
    c.difficulty = m_difficulty->currentData().toString();
    c.hardcore = m_hardcore->isChecked();
    c.allowNether = m_nether->isChecked();
    c.generateStructures = m_structures->isChecked();
    c.spawnProtection = m_spawnProtection->value();
    c.maxPlayers = m_maxPlayers->value();
    c.onlineMode = m_onlineMode->isChecked();
    c.whitelist = m_whitelist->isChecked();
    c.pvp = m_pvp->isChecked();
    c.allowFlight = m_flight->isChecked();
    c.forceGamemode = m_forceGamemode->isChecked();
    c.viewDistance = m_viewDistance->value();
    c.simulationDistance = m_simulationDistance->value();
    c.idleTimeout = m_idle->value();
    c.spawnMonsters = m_monsters->isChecked();
    c.spawnAnimals = m_animals->isChecked();
    c.spawnNpcs = m_villagers->isChecked();
    c.commandBlocks = m_commandBlocks->isChecked();
    c.enforceSecureProfile = m_secureChat->isChecked();
    c.javaPath = m_java->currentData().toString();
    c.jvmArgs = m_jvmArgs->text().trimmed();
    return c;
}

bool ServerSettingsDialog::makeOperator() const
{
    return m_operator && m_operator->isChecked();
}
