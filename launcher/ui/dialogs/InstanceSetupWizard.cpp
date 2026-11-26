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

#include "InstanceSetupWizard.h"

#include <QComboBox>
#include <QHBoxLayout>
#include <QLabel>
#include <QMessageBox>
#include <QPushButton>
#include <QStackedWidget>
#include <QVBoxLayout>

#include "Application.h"
#include "minecraft/MinecraftInstance.h"
#include "minecraft/PackProfile.h"
#include "minecraft/SettingsProfiles.h"
#include "minecraft/mod/ModFolderModel.h"
#include "minecraft/mod/ResourcePackFolderModel.h"
#include "minecraft/mod/ShaderPackFolderModel.h"
#include "settings/SettingsObject.h"
#include "tasks/ConcurrentTask.h"
#include "ui/dialogs/CustomMessageBox.h"
#include "ui/dialogs/ProgressDialog.h"
#include "ui/dialogs/ResourceDownloadDialog.h"
#include "ui/dialogs/SettingsProfilesDialog.h"

using ResourceDownload::ResourceDownloadDialog;

bool InstanceSetupWizard::worthShowing(MinecraftInstance* instance)
{
    return instance != nullptr;
}

InstanceSetupWizard::InstanceSetupWizard(MinecraftInstance* instance, QWidget* parent) : QDialog(parent), m_instance(instance)
{
    setWindowTitle(tr("Set up %1").arg(instance->name()));
    resize(560, 360);

    auto* layout = new QVBoxLayout(this);
    layout->setSpacing(12);

    m_steps = new QLabel(this);
    layout->addWidget(m_steps);

    m_pages = new QStackedWidget(this);
    m_pages->addWidget(browsePage(Mods, tr("Mods"),
                                  modsPossible() ? tr("Pick the mods you want. Dependencies come along on their own. You can add more "
                                                      "any time in the instance's Mods page.")
                                                 : tr("This instance has no mod loader (Fabric, Forge, ...), so it can't use mods. "
                                                      "You can add a loader later in the instance's Version page."),
                                  tr("Browse mods...")));
    m_pages->addWidget(browsePage(ResourcePacks, tr("Resource packs"),
                                  tr("Textures and looks for the game. Pick as many as you like, all from Modrinth."),
                                  tr("Browse resource packs...")));
    m_pages->addWidget(browsePage(Shaders, tr("Shaders"),
                                  tr("Shader packs need a shader mod such as Iris (add it in the mods step) to show up in the game."),
                                  tr("Browse shaders...")));
    m_pages->addWidget(profilePage());
    layout->addWidget(m_pages, 1);

    auto* buttons = new QHBoxLayout();
    auto* skip = new QPushButton(tr("Skip setup"), this);
    m_back = new QPushButton(tr("Back"), this);
    m_next = new QPushButton(tr("Next"), this);
    m_next->setDefault(true);
    buttons->addWidget(skip);
    buttons->addStretch(1);
    buttons->addWidget(m_back);
    buttons->addWidget(m_next);
    layout->addLayout(buttons);

    connect(skip, &QPushButton::clicked, this, &QDialog::reject);
    connect(m_back, &QPushButton::clicked, this, &InstanceSetupWizard::back);
    connect(m_next, &QPushButton::clicked, this, &InstanceSetupWizard::next);

    go(modsPossible() ? Mods : ResourcePacks);
}

bool InstanceSetupWizard::modsPossible() const
{
    return m_instance->getPackProfile()->getModLoaders().has_value();
}

QWidget* InstanceSetupWizard::browsePage(Step step, const QString& title, const QString& text, const QString& button)
{
    auto* page = new QWidget(this);
    auto* layout = new QVBoxLayout(page);
    layout->setContentsMargins(0, 0, 0, 0);

    auto* heading = new QLabel(QStringLiteral("<h2>%1</h2>").arg(title), page);
    layout->addWidget(heading);

    auto* about = new QLabel(text, page);
    about->setWordWrap(true);
    layout->addWidget(about);

    auto* open = new QPushButton(button, page);
    open->setProperty("step", int(step));
    open->setEnabled(step != Mods || modsPossible());
    connect(open, &QPushButton::clicked, this, &InstanceSetupWizard::browse);
    layout->addWidget(open, 0, Qt::AlignLeft);

    m_added[step] = new QLabel(page);
    m_added[step]->setWordWrap(true);
    layout->addWidget(m_added[step]);
    layout->addStretch(1);
    return page;
}

QWidget* InstanceSetupWizard::profilePage()
{
    auto* page = new QWidget(this);
    auto* layout = new QVBoxLayout(page);
    layout->setContentsMargins(0, 0, 0, 0);

    layout->addWidget(new QLabel(QStringLiteral("<h2>%1</h2>").arg(tr("Settings profile")), page));
    auto* about = new QLabel(tr("A profile shares your Minecraft settings, key bindings and Yahu mod settings between instances. "
                                "Pick one, or keep this instance on its own settings."),
                             page);
    about->setWordWrap(true);
    layout->addWidget(about);

    auto* row = new QHBoxLayout();
    m_profiles = new QComboBox(page);
    row->addWidget(m_profiles, 1);
    auto* manage = new QPushButton(tr("Manage profiles..."), page);
    connect(manage, &QPushButton::clicked, this, &InstanceSetupWizard::manageProfiles);
    row->addWidget(manage);
    layout->addLayout(row);
    layout->addStretch(1);

    m_added[Profile] = nullptr;
    refreshProfiles(QString());
    return page;
}

void InstanceSetupWizard::refreshProfiles(const QString& select)
{
    const QString keep = select.isEmpty() ? m_profiles->currentData().toString() : select;
    m_profiles->clear();
    m_profiles->addItem(tr("Own settings (only this instance)"), QString());
    for (const QString& name : SettingsProfiles::names()) {
        m_profiles->addItem(name, name);
    }
    const int index = m_profiles->findData(keep);
    m_profiles->setCurrentIndex(index < 0 ? 0 : index);
}

void InstanceSetupWizard::manageProfiles()
{
    SettingsProfilesDialog(this).exec();
    refreshProfiles(QString());
}

void InstanceSetupWizard::go(int index)
{
    m_pages->setCurrentIndex(index);
    updateButtons();
}

void InstanceSetupWizard::updateButtons()
{
    const int index = m_pages->currentIndex();
    static const QStringList names = { tr("Mods"), tr("Resource packs"), tr("Shaders"), tr("Settings profile") };
    QStringList parts;
    for (int i = 0; i < names.size(); i++) {
        parts << (i == index ? QStringLiteral("<b>%1 %2</b>").arg(i + 1).arg(names[i]) : QStringLiteral("%1 %2").arg(i + 1).arg(names[i]));
    }
    m_steps->setText(parts.join(QStringLiteral("  &rsaquo;  ")));

    const int first = modsPossible() ? Mods : ResourcePacks;
    m_back->setEnabled(index > first);
    m_next->setText(index == Profile ? tr("Finish") : (m_addedNames[index].isEmpty() ? tr("Skip") : tr("Next")));
}

void InstanceSetupWizard::back()
{
    go(m_pages->currentIndex() - 1);
}

void InstanceSetupWizard::next()
{
    if (m_pages->currentIndex() == Profile) {
        finish();
        return;
    }
    go(m_pages->currentIndex() + 1);
}

void InstanceSetupWizard::finish()
{
    m_instance->settings()->set(SettingsProfiles::kSetting, m_profiles->currentData().toString());
    accept();
}

void InstanceSetupWizard::browse()
{
    const int step = sender()->property("step").toInt();

    ResourceFolderModel* model = nullptr;
    ResourceDownloadDialog* dialog = nullptr;
    switch (step) {
        case Mods:
            model = m_instance->loaderModList();
            dialog = ResourceDownloadDialog::createMod(this, model, m_instance);
            break;
        case ResourcePacks:
            model = m_instance->resourcePackList();
            dialog = ResourceDownloadDialog::createResourcePack(this, model, m_instance);
            break;
        case Shaders:
            model = m_instance->shaderPackList();
            dialog = ResourceDownloadDialog::createShaderPack(this, model, m_instance);
            break;
        default:
            return;
    }
    model->startWatching();

    const bool accepted = dialog->exec() == QDialog::Accepted;
    if (accepted) {
        ConcurrentTask tasks(tr("Download"), APPLICATION->settings()->get("NumberOfConcurrentDownloads").toInt());
        connect(&tasks, &Task::failed, this,
                [this](const QString& reason) { CustomMessageBox::selectable(this, tr("Error"), reason, QMessageBox::Critical)->show(); });
        QStringList names;
        for (auto& task : dialog->getTasks()) {
            names << task->getName();
            tasks.addTask(task);
        }
        if (!names.isEmpty()) {
            ProgressDialog progress(this);
            progress.showSkipButton();
            progress.execWithTask(&tasks);
            model->update();

            m_addedNames[step] += names;
            m_added[step]->setText(tr("Added %n item(s): %1", nullptr, int(m_addedNames[step].size())).arg(m_addedNames[step].join(QStringLiteral(", "))));
        }
    }
    dialog->deleteLater();
    updateButtons();
}
