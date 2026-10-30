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

#include "YahuServersPage.h"

#include <QApplication>
#include <QClipboard>
#include <QFontDatabase>
#include <QFrame>
#include <QGridLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QLineEdit>
#include <QListWidget>
#include <QMessageBox>
#include <QPainter>
#include <QPlainTextEdit>
#include <QPushButton>
#include <QScrollBar>
#include <QStackedWidget>
#include <QStyledItemDelegate>
#include <QVBoxLayout>

#include "Application.h"
#include "DesktopServices.h"
#include "YahuStyle.h"
#include "minecraft/auth/AccountList.h"
#include "servers/ServerManager.h"
#include "ui/dialogs/ServerSettingsDialog.h"

using namespace YahuStyle;

namespace {
constexpr int kServerRole = Qt::UserRole + 1;

QColor stateColor(YahuServer::State state)
{
    switch (state) {
        case YahuServer::State::Running:
            return accent;
        case YahuServer::State::Starting:
        case YahuServer::State::Stopping:
        case YahuServer::State::Installing:
            return gold;
        case YahuServer::State::Failed:
            return danger;
        case YahuServer::State::Stopped:
        default:
            return faint;
    }
}

QString subtitleFor(const ServerConfig& c)
{
    return QObject::tr("%1 %2").arg(ServerConfig::softwareName(c.software), c.mcVersion);
}

// Two lines per server: the name, and software plus state with a colored dot.
class ServerItemDelegate : public QStyledItemDelegate {
   public:
    using QStyledItemDelegate::QStyledItemDelegate;

    QSize sizeHint(const QStyleOptionViewItem&, const QModelIndex&) const override { return { 200, 58 }; }

    void paint(QPainter* p, const QStyleOptionViewItem& option, const QModelIndex& index) const override
    {
        auto* server = index.data(kServerRole).value<YahuServer*>();
        if (!server) {
            return;
        }
        p->save();
        p->setRenderHint(QPainter::Antialiasing, true);
        const QRectF r = QRectF(option.rect).adjusted(0, 2, 0, -2);
        const bool selected = option.state & QStyle::State_Selected;
        const bool hovered = option.state & QStyle::State_MouseOver;
        if (selected || hovered) {
            p->setPen(Qt::NoPen);
            p->setBrush(selected ? raisedHi : raised);
            p->drawRoundedRect(r, 10, 10);
        }
        QFont nameFont = option.font;
        nameFont.setWeight(QFont::DemiBold);
        p->setFont(nameFont);
        p->setPen(text);
        p->drawText(QRectF(r.left() + 14, r.top() + 8, r.width() - 28, 20), Qt::AlignLeft | Qt::AlignVCenter,
                    QFontMetrics(nameFont).elidedText(server->config().name, Qt::ElideRight, int(r.width() - 28)));

        const QColor dot = stateColor(server->state());
        p->setPen(Qt::NoPen);
        p->setBrush(dot);
        p->drawEllipse(QPointF(r.left() + 18, r.top() + 38), 3.5, 3.5);
        QFont small = option.font;
        small.setPointSizeF(small.pointSizeF() - 1);
        p->setFont(small);
        p->setPen(muted);
        p->drawText(QRectF(r.left() + 28, r.top() + 29, r.width() - 42, 18), Qt::AlignLeft | Qt::AlignVCenter,
                    QObject::tr("%1 · %2").arg(server->stateText(), subtitleFor(server->config())));
        p->restore();
    }
};

QFrame* panel()
{
    auto* frame = new QFrame;
    frame->setObjectName(QStringLiteral("yahuPanel"));
    return frame;
}

QLabel* caption(const QString& text)
{
    auto* label = new QLabel(text);
    label->setProperty("yahuHint", true);
    return label;
}
}  // namespace

YahuServersPage::YahuServersPage(ServerManager* manager, QWidget* parent) : QWidget(parent), m_manager(manager)
{
    setAttribute(Qt::WA_TranslucentBackground, true);
    auto* root = new QHBoxLayout(this);
    root->setContentsMargins(28, 24, 28, 24);
    root->setSpacing(18);

    // ----- list of servers
    {
        auto* side = panel();
        side->setFixedWidth(270);
        auto* layout = new QVBoxLayout(side);
        layout->setContentsMargins(14, 16, 14, 14);
        layout->setSpacing(12);
        auto* title = new QLabel(tr("Servers"));
        title->setFont(displayFont(15, QFont::Bold));
        layout->addWidget(title);
        auto* create = new QPushButton(tr("New server"));
        create->setDefault(true);
        create->setCursor(Qt::PointingHandCursor);
        connect(create, &QPushButton::clicked, this, &YahuServersPage::createServer);
        layout->addWidget(create);
        m_list = new QListWidget;
        m_list->setObjectName(QStringLiteral("yahuServerList"));
        m_list->setItemDelegate(new ServerItemDelegate(m_list));
        m_list->setMouseTracking(true);
        m_list->setVerticalScrollMode(QAbstractItemView::ScrollPerPixel);
        layout->addWidget(m_list, 1);
        connect(m_list, &QListWidget::currentItemChanged, this, [this](QListWidgetItem* item) {
            select(item ? item->data(kServerRole).value<YahuServer*>() : nullptr);
        });
        root->addWidget(side);
    }

    m_stack = new QStackedWidget;
    root->addWidget(m_stack, 1);

    // ----- empty state
    {
        auto* empty = new QWidget;
        auto* layout = new QVBoxLayout(empty);
        layout->addStretch(1);
        auto* title = new QLabel(tr("No servers yet"));
        title->setFont(displayFont(20, QFont::Bold));
        title->setAlignment(Qt::AlignCenter);
        auto* body = new QLabel(tr("Create a server for you and your friends. It runs on this PC while Yahu Client is open, "
                                   "and your friends join with the address shown here."));
        body->setWordWrap(true);
        body->setAlignment(Qt::AlignCenter);
        body->setFixedWidth(460);
        body->setProperty("yahuHint", true);
        auto* create = new QPushButton(tr("Create a server"));
        create->setDefault(true);
        create->setCursor(Qt::PointingHandCursor);
        connect(create, &QPushButton::clicked, this, &YahuServersPage::createServer);
        layout->addWidget(title);
        layout->addWidget(body, 0, Qt::AlignHCenter);
        layout->addSpacing(8);
        layout->addWidget(create, 0, Qt::AlignHCenter);
        layout->addStretch(2);
        m_stack->addWidget(empty);
    }

    // ----- one server
    {
        auto* detail = new QWidget;
        auto* layout = new QVBoxLayout(detail);
        layout->setContentsMargins(0, 0, 0, 0);
        layout->setSpacing(14);

        auto* header = new QHBoxLayout;
        auto* titles = new QVBoxLayout;
        titles->setSpacing(2);
        m_name = new QLabel;
        m_name->setFont(displayFont(20, QFont::Bold));
        m_subtitle = caption(QString());
        titles->addWidget(m_name);
        titles->addWidget(m_subtitle);
        header->addLayout(titles, 1);
        m_status = new QLabel;
        header->addWidget(m_status);
        header->addSpacing(8);
        m_startStop = new QPushButton;
        m_startStop->setCursor(Qt::PointingHandCursor);
        m_startStop->setMinimumWidth(110);
        m_settings = new QPushButton(tr("Settings"));
        m_folder = new QPushButton(tr("Folder"));
        m_delete = new QPushButton(tr("Delete"));
        for (auto* b : { m_settings, m_folder, m_delete }) {
            b->setCursor(Qt::PointingHandCursor);
        }
        header->addWidget(m_startStop);
        header->addWidget(m_settings);
        header->addWidget(m_folder);
        header->addWidget(m_delete);
        layout->addLayout(header);

        m_detail = caption(QString());
        m_detail->setWordWrap(true);
        layout->addWidget(m_detail);

        // how friends join
        auto* join = panel();
        auto* grid = new QGridLayout(join);
        grid->setContentsMargins(18, 14, 18, 14);
        grid->setHorizontalSpacing(14);
        grid->setVerticalSpacing(8);
        auto* joinTitle = new QLabel(tr("How your friends join"));
        QFont bold = joinTitle->font();
        bold.setWeight(QFont::DemiBold);
        joinTitle->setFont(bold);
        grid->addWidget(joinTitle, 0, 0, 1, 3);
        grid->addWidget(caption(tr("Same network")), 1, 0);
        m_lanAddress = new QLabel;
        m_lanAddress->setTextInteractionFlags(Qt::TextSelectableByMouse);
        grid->addWidget(m_lanAddress, 1, 1);
        m_copyLan = new QPushButton(tr("Copy"));
        grid->addWidget(m_copyLan, 1, 2);
        grid->addWidget(caption(tr("Over the internet")), 2, 0);
        m_publicAddress = new QLabel;
        m_publicAddress->setTextInteractionFlags(Qt::TextSelectableByMouse);
        m_publicAddress->setWordWrap(true);
        grid->addWidget(m_publicAddress, 2, 1);
        m_publicButton = new QPushButton(tr("Show"));
        grid->addWidget(m_publicButton, 2, 2);
        grid->addWidget(caption(tr("Online")), 3, 0);
        m_players = new QLabel;
        m_players->setWordWrap(true);
        grid->addWidget(m_players, 3, 1, 1, 2);
        grid->setColumnStretch(1, 1);
        layout->addWidget(join);

        // console
        auto* consolePanel = panel();
        auto* consoleLayout = new QVBoxLayout(consolePanel);
        consoleLayout->setContentsMargins(14, 12, 14, 14);
        consoleLayout->setSpacing(10);
        auto* consoleTitle = new QLabel(tr("Console"));
        consoleTitle->setFont(bold);
        consoleLayout->addWidget(consoleTitle);
        m_console = new QPlainTextEdit;
        m_console->setReadOnly(true);
        m_console->setMaximumBlockCount(1500);
        m_console->setLineWrapMode(QPlainTextEdit::NoWrap);
        m_console->setFont(QFontDatabase::systemFont(QFontDatabase::FixedFont));
        consoleLayout->addWidget(m_console, 1);
        m_command = new QLineEdit;
        m_command->setPlaceholderText(tr("Type a command and press Enter, e.g. whitelist add Steve"));
        consoleLayout->addWidget(m_command);
        layout->addWidget(consolePanel, 1);

        m_stack->addWidget(detail);
    }

    connect(m_startStop, &QPushButton::clicked, this, [this] {
        if (!m_current) {
            return;
        }
        if (m_current->state() == YahuServer::State::Running || m_current->state() == YahuServer::State::Starting) {
            m_current->stop();
        } else if (!m_current->isBusy()) {
            m_manager->start(m_current);
        }
    });
    connect(m_settings, &QPushButton::clicked, this, &YahuServersPage::editServer);
    connect(m_folder, &QPushButton::clicked, this, [this] {
        if (m_current) {
            DesktopServices::openPath(m_current->dir(), true);
        }
    });
    connect(m_delete, &QPushButton::clicked, this, &YahuServersPage::deleteServer);
    connect(m_copyLan, &QPushButton::clicked, this,
            [this] { QApplication::clipboard()->setText(withPort(ServerManager::lanAddress())); });
    connect(m_publicButton, &QPushButton::clicked, this, [this] {
        if (!m_manager->publicAddress().isEmpty()) {
            QApplication::clipboard()->setText(withPort(m_manager->publicAddress()));
            return;
        }
        m_publicButton->setEnabled(false);
        m_publicAddress->setText(tr("Looking it up…"));
        m_manager->fetchPublicAddress();
    });
    connect(m_manager, &ServerManager::publicAddressChanged, this, [this](const QString& error) {
        m_publicButton->setEnabled(true);
        if (!error.isEmpty()) {
            m_publicAddress->setText(tr("Couldn't find it out: %1").arg(error));
            return;
        }
        refreshAddresses();
    });
    connect(m_command, &QLineEdit::returnPressed, this, [this] {
        if (m_current) {
            m_current->sendCommand(m_command->text());
            m_command->clear();
        }
    });
    connect(m_manager, &ServerManager::serversChanged, this, &YahuServersPage::rebuildList);

    rebuildList();
}

QString YahuServersPage::withPort(const QString& host) const
{
    // 25565 is Minecraft's default, so friends don't need to type it
    const int port = m_current ? m_current->config().port : 25565;
    return port == 25565 ? host : QStringLiteral("%1:%2").arg(host).arg(port);
}

void YahuServersPage::rebuildList()
{
    YahuServer* keep = m_current;
    m_list->blockSignals(true);
    m_list->clear();
    QListWidgetItem* toSelect = nullptr;
    for (auto* server : m_manager->servers()) {
        auto* item = new QListWidgetItem(server->config().name, m_list);
        item->setData(kServerRole, QVariant::fromValue(server));
        if (server == keep) {
            toSelect = item;
        }
        connect(server, &YahuServer::stateChanged, m_list->viewport(), QOverload<>::of(&QWidget::update), Qt::UniqueConnection);
    }
    m_list->blockSignals(false);
    if (!toSelect && m_list->count() > 0) {
        toSelect = m_list->item(m_list->count() - 1);
    }
    if (toSelect) {
        m_list->setCurrentItem(toSelect);
        select(toSelect->data(kServerRole).value<YahuServer*>());
    } else {
        select(nullptr);
    }
}

void YahuServersPage::select(YahuServer* server)
{
    for (const auto& c : m_serverConnections) {
        disconnect(c);
    }
    m_serverConnections.clear();
    m_current = server;
    if (!server) {
        m_stack->setCurrentIndex(0);
        return;
    }
    m_stack->setCurrentIndex(1);
    m_console->setPlainText(server->log().join('\n'));
    m_console->verticalScrollBar()->setValue(m_console->verticalScrollBar()->maximum());
    m_serverConnections << connect(server, &YahuServer::stateChanged, this, &YahuServersPage::refreshDetail);
    m_serverConnections << connect(server, &YahuServer::playersChanged, this, &YahuServersPage::refreshPlayers);
    m_serverConnections << connect(server, &YahuServer::logAppended, this, [this](const QString& line) {
        auto* bar = m_console->verticalScrollBar();
        const bool atBottom = bar->value() >= bar->maximum() - 4;
        m_console->appendPlainText(line);
        if (atBottom) {
            bar->setValue(bar->maximum());
        }
    });
    refreshDetail();
}

void YahuServersPage::refreshDetail()
{
    if (!m_current) {
        return;
    }
    const auto& c = m_current->config();
    const auto state = m_current->state();
    m_name->setText(c.name);
    m_subtitle->setText(tr("%1 · %2 GB memory · %3")
                            .arg(subtitleFor(c), QString::number(c.memoryMb / 1024.0, 'g', 3),
                                 c.maxPlayers == 1 ? tr("1 player") : tr("up to %1 players").arg(c.maxPlayers)));
    m_status->setText(QStringLiteral("<span style=\"color:%1\">●</span>&nbsp; %2").arg(stateColor(state).name(), m_current->stateText()));

    const bool running = state == YahuServer::State::Running || state == YahuServer::State::Starting;
    m_startStop->setText(running ? tr("Stop") : (m_current->isInstalled() ? tr("Start") : tr("Install")));
    m_startStop->setDefault(!running);
    m_startStop->setEnabled(state != YahuServer::State::Installing && state != YahuServer::State::Stopping);
    m_settings->setEnabled(!m_current->isBusy());
    m_delete->setEnabled(!m_current->isBusy());
    m_command->setEnabled(state == YahuServer::State::Running || state == YahuServer::State::Starting);

    QString detail;
    if (state == YahuServer::State::Installing || state == YahuServer::State::Failed) {
        detail = m_current->detail();
    } else if (state == YahuServer::State::Starting) {
        detail = tr("Starting up. The first start creates the world, that can take a minute.");
    } else if (state == YahuServer::State::Stopped && !m_current->isInstalled()) {
        detail = tr("The server software isn't downloaded yet. Press Install.");
    }
    m_detail->setText(detail);
    m_detail->setVisible(!detail.isEmpty());
    m_list->viewport()->update();
    refreshAddresses();
    refreshPlayers();
}

void YahuServersPage::refreshAddresses()
{
    if (!m_current) {
        return;
    }
    m_lanAddress->setText(withPort(ServerManager::lanAddress()));
    if (m_manager->publicAddress().isEmpty()) {
        m_publicAddress->setText(tr("Your public IP is looked up only when you ask."));
        m_publicButton->setText(tr("Show"));
    } else {
        m_publicAddress->setText(tr("%1\nYour router must forward TCP port %2 to this PC for this to work.")
                                     .arg(withPort(m_manager->publicAddress()))
                                     .arg(m_current->config().port));
        m_publicButton->setText(tr("Copy"));
    }
}

void YahuServersPage::refreshPlayers()
{
    if (!m_current) {
        return;
    }
    const QStringList players = m_current->players();
    if (m_current->state() != YahuServer::State::Running) {
        m_players->setText(tr("Server is offline"));
    } else if (players.isEmpty()) {
        m_players->setText(tr("Nobody yet"));
    } else {
        m_players->setText(tr("%1 of %2: %3").arg(players.size()).arg(m_current->config().maxPlayers).arg(players.join(", ")));
    }
}

void YahuServersPage::createServer()
{
    ServerSettingsDialog dialog(m_manager, nullptr, this);
    if (dialog.exec() != QDialog::Accepted) {
        return;
    }
    QString opName, opUuid;
    if (dialog.makeOperator()) {
        if (const auto account = APPLICATION->accounts()->defaultAccount()) {
            opName = account->profileName();
            opUuid = account->profileId();
        }
    }
    auto* server = m_manager->create(dialog.config(), opName, opUuid);
    for (int i = 0; i < m_list->count(); ++i) {
        if (m_list->item(i)->data(kServerRole).value<YahuServer*>() == server) {
            m_list->setCurrentRow(i);
        }
    }
}

void YahuServersPage::editServer()
{
    if (!m_current || m_current->isBusy()) {
        return;
    }
    ServerSettingsDialog dialog(m_manager, &m_current->config(), this);
    if (dialog.exec() == QDialog::Accepted && m_current) {
        m_manager->update(m_current, dialog.config());
        refreshDetail();
    }
}

void YahuServersPage::deleteServer()
{
    if (!m_current) {
        return;
    }
    const auto answer = QMessageBox::question(
        this, tr("Delete server"),
        tr("Delete \"%1\"? Its folder, including the world, goes to the recycle bin, so you can still get it back from there.")
            .arg(m_current->config().name),
        QMessageBox::Yes | QMessageBox::No, QMessageBox::No);
    if (answer != QMessageBox::Yes || !m_current) {
        return;
    }
    QString error;
    if (!m_manager->remove(m_current, &error)) {
        QMessageBox::warning(this, tr("Delete server"), error);
    }
}
