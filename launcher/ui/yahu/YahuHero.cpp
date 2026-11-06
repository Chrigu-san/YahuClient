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

#include "YahuHero.h"

#include <QAction>
#include <QApplication>
#include <QHBoxLayout>
#include <QMenu>
#include <QMouseEvent>
#include <QPainter>
#include <QPainterPath>
#include <QToolButton>
#include <QVBoxLayout>
#include <QtMath>

#include <algorithm>
#include <array>

#include "Application.h"
#include "BaseInstance.h"
#include "InstanceList.h"
#include "YahuPlayButton.h"
#include "YahuStyle.h"
#include "icons/IconList.h"
#include "minecraft/auth/AccountList.h"

using namespace YahuStyle;

namespace {
constexpr int kHeight = 300;
constexpr int kMarginX = 28;      // banner inset from the hero's edges
constexpr int kMarginTop = 24;
constexpr int kMarginBottom = 8;
constexpr int kInnerPad = 32;     // text inset inside the banner
constexpr qreal kOrbit = 84.0;    // radius of the comet orbit around the instance icon
constexpr qreal kPlate = 112.0;   // edge length of the glass plate behind the icon
constexpr qreal kTau = 6.283185307179586;

// Cheap deterministic noise so the landscape looks the same every frame.
qreal hash(int n)
{
    n = (n << 13) ^ n;
    return 1.0 - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0;
}
}  // namespace

YahuHero::YahuHero(QAction* launchAction,
                   QAction* killAction,
                   QAction* editAction,
                   QAction* folderAction,
                   const QList<QAction*>& moreActions,
                   QWidget* parent)
    : QWidget(parent), m_launchAction(launchAction), m_killAction(killAction), m_editAction(editAction), m_folderAction(folderAction)
{
    setObjectName(QStringLiteral("yahuHero"));
    setFixedHeight(kHeight);
    setAttribute(Qt::WA_TranslucentBackground, true);

    m_play = new YahuPlayButton(this);
    connect(m_play, &QAbstractButton::clicked, this, [this] {
        if (!m_instance && m_libraryEmpty && m_createAction) {
            m_createAction->trigger();
        } else {
            m_launchAction->trigger();
        }
    });

    m_launchMenuButton = makeGhostButton(QString(), QString());
    m_launchMenuButton->setIcon(QIcon(QStringLiteral(":/yahu/chevron-down.svg")));
    m_launchMenuButton->setIconSize(QSize(16, 16));
    m_launchMenuButton->setToolButtonStyle(Qt::ToolButtonIconOnly);
    m_launchMenuButton->setToolTip(tr("More ways to launch"));
    m_launchMenuButton->setFixedWidth(46);
    connect(m_launchMenuButton, &QToolButton::clicked, this, [this] {
        // the launch menu is rebuilt whenever the selection changes, so look it up at click time
        if (auto* menu = m_launchAction->menu()) {
            menu->popup(m_launchMenuButton->mapToGlobal(QPoint(0, m_launchMenuButton->height() + 6)));
        }
    });

    m_stopButton = makeGhostButton(tr("Stop"), QStringLiteral("kill"));
    m_stopButton->setObjectName(QStringLiteral("yahuStop"));
    connect(m_stopButton, &QToolButton::clicked, m_killAction, &QAction::trigger);
    m_editButton = makeGhostButton(QString(), QStringLiteral("instance-settings"));
    m_editButton->setToolTip(tr("Edit instance"));
    m_editButton->setFixedWidth(46);
    connect(m_editButton, &QToolButton::clicked, m_editAction, &QAction::trigger);
    m_folderButton = makeGhostButton(QString(), QStringLiteral("viewfolder"));
    m_folderButton->setToolTip(tr("Open instance folder"));
    m_folderButton->setFixedWidth(46);
    connect(m_folderButton, &QToolButton::clicked, m_folderAction, &QAction::trigger);

    m_moreMenu = new QMenu(this);
    m_moreMenu->addActions(moreActions);
    m_moreButton = makeGhostButton(QString(), QString());
    m_moreButton->setText(QStringLiteral("•••"));
    m_moreButton->setToolTip(tr("More actions"));
    m_moreButton->setFixedWidth(46);
    connect(m_moreButton, &QToolButton::clicked, this,
            [this] { m_moreMenu->popup(m_moreButton->mapToGlobal(QPoint(0, m_moreButton->height() + 6))); });

    auto* buttons = new QHBoxLayout;
    buttons->setContentsMargins(0, 0, 0, 0);
    buttons->setSpacing(8);
    buttons->addWidget(m_play);
    buttons->addWidget(m_launchMenuButton);
    buttons->addSpacing(4);
    buttons->addWidget(m_stopButton);
    buttons->addWidget(m_editButton);
    buttons->addWidget(m_folderButton);
    buttons->addWidget(m_moreButton);
    buttons->addStretch(1);

    auto* layout = new QVBoxLayout(this);
    // the text block is painted; the buttons sit underneath it, inside the banner (the play button has a 12px glow pad)
    layout->setContentsMargins(kMarginX + kInnerPad - 12, 0, kMarginX + kInnerPad, kMarginBottom + 16);
    layout->addStretch(1);
    layout->addLayout(buttons);

    setStyleSheet(QStringLiteral(R"QSS(
        QToolButton[yahuGhost="true"] {
            background: %1; color: %2; border: 1px solid %3;
            border-radius: 10px; padding: 0px 14px; min-height: 46px; font-weight: 700;
        }
        QToolButton[yahuGhost="true"]:hover { border-color: %4; background: %5; }
        QToolButton[yahuGhost="true"]:pressed { background: %3; }
        QToolButton[yahuGhost="true"]:disabled { color: %6; }
        QToolButton#yahuStop { color: %7; border-color: %7; }
    )QSS")
                      .arg(withAlpha(ink, 0.72).name(QColor::HexArgb), text.name(), withAlpha(text, 0.14).name(QColor::HexArgb),
                           withAlpha(text, 0.35).name(QColor::HexArgb), withAlpha(raisedHi, 0.9).name(QColor::HexArgb), faint.name(),
                           danger.name()));

    for (auto* action : { m_launchAction, m_killAction, m_editAction, m_folderAction }) {
        connect(action, &QAction::changed, this, &YahuHero::syncActions);
    }

    connect(&m_swapAnimation, &QVariantAnimation::valueChanged, this, [this](const QVariant& v) {
        m_swap = v.toReal();
        m_spin = m_swap * 140.0;  // the comet sprints ahead while the new selection slides in
        update();
    });

    m_frameTimer.setInterval(16);
    m_frameTimer.setTimerType(Qt::PreciseTimer);
    connect(&m_frameTimer, &QTimer::timeout, this, [this] {
        m_look += (m_lookTarget - m_look) * 0.06;
        update(bannerRect().toAlignedRect());
    });
    m_clock.start();
    qApp->installEventFilter(this);

    setInstance(nullptr);
}

QToolButton* YahuHero::makeGhostButton(const QString& label, const QString& iconName)
{
    auto* button = new QToolButton(this);
    button->setProperty("yahuGhost", true);
    button->setText(label);
    if (!iconName.isEmpty()) {
        button->setIcon(QIcon::fromTheme(iconName));
        button->setIconSize(QSize(18, 18));
    }
    button->setCursor(Qt::PointingHandCursor);
    button->setToolButtonStyle(label.isEmpty() ? Qt::ToolButtonIconOnly : Qt::ToolButtonTextBesideIcon);
    return button;
}

QRectF YahuHero::bannerRect() const
{
    return QRectF(rect()).adjusted(kMarginX, kMarginTop, -kMarginX, -kMarginBottom);
}

void YahuHero::setCreateAction(QAction* createAction)
{
    m_createAction = createAction;
    connect(createAction, &QAction::changed, this, &YahuHero::syncActions);
    refresh();
}

void YahuHero::setInstance(BaseInstance* instance)
{
    const bool changed = instance != m_instance.data();
    m_instance = instance;
    refresh();
    if (changed) {
        m_swapAnimation.stop();
        m_swapAnimation.setStartValue(0.0);
        m_swapAnimation.setEndValue(1.0);
        m_swapAnimation.setDuration(700);
        m_swapAnimation.setEasingCurve(QEasingCurve::OutQuint);
        m_swapAnimation.start();
    }
}

void YahuHero::refresh()
{
    m_libraryEmpty = APPLICATION->instances()->count() == 0;
    if (m_instance) {
        m_running = m_instance->isRunning();
        m_title = m_instance->name();
        m_meta = m_instance->getStatusbarDescription();
        m_icon = APPLICATION->icons()->getIcon(m_instance->iconKey());
    } else if (m_libraryEmpty) {
        m_running = false;
        m_title = tr("No instances yet");
        m_meta = tr("Create one to start playing. Pick a Minecraft version and you're set.");
        m_icon = QIcon(QStringLiteral(":/yahu/logo.svg"));
    } else {
        m_running = false;
        const auto account = APPLICATION->accounts()->defaultAccount();
        const QString name = account ? account->profileName() : QString();
        m_title = name.isEmpty() ? tr("Welcome back") : tr("Welcome back, %1").arg(name);
        m_meta = tr("Pick an instance below.");
        m_icon = QIcon(QStringLiteral(":/yahu/logo.svg"));
    }
    syncActions();
    update();
}

void YahuHero::syncActions()
{
    const bool creating = !m_instance && m_libraryEmpty && m_createAction;
    if (creating) {
        m_play->setMode(YahuPlayButton::Mode::Create);
        m_play->setEnabled(m_createAction->isEnabled());
        m_play->setToolTip(tr("Create your first instance"));
    } else {
        m_play->setMode(m_running ? YahuPlayButton::Mode::Running : YahuPlayButton::Mode::Play);
        m_play->setEnabled(m_instance && m_launchAction->isEnabled() && !m_running);
        m_play->setToolTip(m_instance ? tr("Launch %1").arg(m_instance->name()) : tr("Select an instance first"));
    }
    // instance tools only make sense once something is selected
    const bool hasInstance = m_instance;
    m_launchMenuButton->setVisible(hasInstance && !m_running);
    m_launchMenuButton->setEnabled(m_launchAction->isEnabled());
    m_stopButton->setVisible(m_running && m_killAction->isEnabled());
    m_editButton->setVisible(hasInstance);
    m_editButton->setEnabled(m_editAction->isEnabled());
    m_folderButton->setVisible(hasInstance);
    m_folderButton->setEnabled(m_folderAction->isEnabled());
    m_moreButton->setVisible(hasInstance);
}

void YahuHero::updateRunning()
{
    const bool animate = isVisible() && window()->isActiveWindow() && !window()->isMinimized();
    if (animate && !m_frameTimer.isActive()) {
        m_frameTimer.start();
    } else if (!animate) {
        m_frameTimer.stop();
    }
}

void YahuHero::showEvent(QShowEvent* event)
{
    QWidget::showEvent(event);
    updateRunning();
}

void YahuHero::hideEvent(QHideEvent* event)
{
    QWidget::hideEvent(event);
    m_frameTimer.stop();
}

void YahuHero::changeEvent(QEvent* event)
{
    if (event->type() == QEvent::ActivationChange) {
        updateRunning();
    }
    QWidget::changeEvent(event);
}

bool YahuHero::eventFilter(QObject* watched, QEvent* event)
{
    if (event->type() == QEvent::MouseMove) {
        auto* w = qobject_cast<QWidget*>(watched);
        if (w && w->window() == window()) {
            // the landscape and the block react to the cursor wherever it is in the window
            const QPointF local = mapFromGlobal(static_cast<QMouseEvent*>(event)->globalPosition());
            const QRectF banner = bannerRect();
            m_lookTarget = QPointF(qBound(-1.0, (local.x() - banner.center().x()) / (banner.width() / 2), 1.0),
                                   qBound(-1.0, (local.y() - banner.center().y()) / 300.0, 1.0));
        }
    }
    return false;
}

void YahuHero::paintEmblem(QPainter& p, const QPointF& center, qreal t)
{
    // the instance icon on a dark block, circled by a slow comet; flat on purpose, nothing here can shimmer
    const QColor tone = m_running ? gold : accent;
    const qreal bob = qSin(t * kTau / 4.0) * 4.0;
    const QPointF plateCenter = center + QPointF(0, bob);

    // orbit track
    p.setBrush(Qt::NoBrush);
    p.setPen(QPen(withAlpha(text, 0.06), 1.0));
    p.drawEllipse(center, kOrbit, kOrbit);

    // the comet runs clockwise; its tail fades out behind it
    const qreal head = -std::fmod(t * (m_running ? 150.0 : 42.0) + m_spin, 360.0);
    const QRectF ring(center.x() - kOrbit, center.y() - kOrbit, kOrbit * 2, kOrbit * 2);
    constexpr int kTail = 30;
    for (int i = kTail - 1; i >= 0; --i) {
        const qreal fade = 1.0 - qreal(i) / kTail;
        p.setPen(QPen(withAlpha(tone, 0.75 * fade * fade), 2.0, Qt::SolidLine, Qt::RoundCap));
        p.drawArc(ring, int((head + i * 3.0) * 16), 3 * 16 + 4);
    }
    const qreal rad = qDegreesToRadians(head);
    const QPointF dot = center + QPointF(qCos(rad), -qSin(rad)) * kOrbit;
    p.setPen(Qt::NoPen);
    QRadialGradient spark(dot, 12);
    spark.setColorAt(0.0, withAlpha(tone, 0.55));
    spark.setColorAt(1.0, withAlpha(tone, 0.0));
    p.setBrush(spark);
    p.drawEllipse(dot, 12, 12);
    p.setBrush(mix(tone, text, 0.5));
    p.drawEllipse(dot, 2.6, 2.6);

    // glass plate
    const QRectF plate(plateCenter.x() - kPlate / 2, plateCenter.y() - kPlate / 2, kPlate, kPlate);
    for (int i = 3; i >= 1; --i) {
        p.setBrush(withAlpha(Qt::black, 0.16));
        p.drawPath(rounded(plate.adjusted(-i * 2, -i + 8, i * 2, i * 2 + 8), 26 + i * 2));
    }
    QLinearGradient glass(plate.topLeft(), plate.bottomLeft());
    glass.setColorAt(0.0, withAlpha(raisedHi, 0.96));
    glass.setColorAt(1.0, withAlpha(ink, 0.96));
    p.setBrush(glass);
    p.setPen(Qt::NoPen);
    p.drawPath(rounded(plate, 26));
    blockEdges(p, plate, 26, 4.0, 1.2);

    // the icon, drawn from a pixmap at fractional positions so the float stays smooth
    const qreal size = 68.0 * (0.82 + 0.18 * m_swap);
    const QPixmap pm = m_icon.pixmap(QSize(68, 68), devicePixelRatioF());
    p.save();
    p.setOpacity(qBound(0.0, m_swap * 1.5, 1.0));
    p.drawPixmap(QRectF(plateCenter.x() - size / 2, plateCenter.y() - size / 2, size, size), pm, QRectF(pm.rect()));
    p.restore();
}

void YahuHero::paintLandscape(QPainter& p, const QRectF& b, qreal t)
{
    const qreal lx = m_look.x();
    const qreal ly = m_look.y();

    // sky in the palette's colors (a sunset or a night), with a glow behind the hills so their silhouettes read
    QLinearGradient sky(b.topLeft(), b.bottomLeft());
    sky.setColorAt(0.0, scene.sky[0]);
    sky.setColorAt(0.35, scene.sky[1]);
    sky.setColorAt(0.7, scene.sky[2]);
    sky.setColorAt(1.0, scene.sky[3]);
    p.fillRect(b, sky);
    {
        QRadialGradient dusk(QPointF(b.center().x() - lx * 20, b.bottom()), b.width() * 0.55);
        dusk.setColorAt(0.0, withAlpha(scene.sun ? gold : accent, scene.sun ? 0.22 : 0.07));
        dusk.setColorAt(1.0, withAlpha(scene.sun ? gold : accent, 0.0));
        p.fillRect(b, dusk);
    }

    // stars, twinkling, barely moving; at sunset only a few, high up
    p.setPen(Qt::NoPen);
    const int stars = scene.sun ? 24 : 70;
    const qreal starHeight = scene.sun ? 0.3 : 0.62;
    for (int i = 0; i < stars; ++i) {
        const qreal x = b.left() + (0.5 + 0.5 * hash(i * 7 + 1)) * b.width() - lx * 6;
        const qreal y = b.top() + (0.5 + 0.5 * hash(i * 13 + 5)) * b.height() * starHeight - ly * 3;
        const qreal tw = 0.35 + 0.65 * (0.5 + 0.5 * qSin(t * (1.0 + 0.8 * (0.5 + 0.5 * hash(i))) + i));
        const qreal s = hash(i * 3) > 0.7 ? 2.0 : 1.4;
        p.setBrush(withAlpha(i % 9 == 0 ? gold : text, 0.55 * tw));
        p.drawRect(QRectF(int(x), int(y), s, s));
    }

    if (scene.sun) {
        // a big square sun sinking behind the hills, with rings of light around it
        const QPointF m(b.left() + b.width() * 0.6 - lx * 10, b.top() + b.height() * 0.4 - ly * 5);
        const qreal s = 44;
        for (int ring = 3; ring >= 1; --ring) {
            const qreal r = s / 2 + ring * 10;
            p.setBrush(withAlpha(QColor(0xFF, 0xD8, 0x6A), 0.08 * (4 - ring)));
            p.drawRect(QRectF(m.x() - r, m.y() - r, r * 2, r * 2));
        }
        p.setBrush(QColor(0xFF, 0xD5, 0x4A));
        p.drawRect(QRectF(m.x() - s / 2, m.y() - s / 2, s, s));
        p.setBrush(QColor(0xFF, 0xF0, 0x9A));
        p.drawRect(QRectF(m.x() - s / 2, m.y() - s / 2, s, 6));
        p.drawRect(QRectF(m.x() - s / 2, m.y() - s / 2, 6, s));
        p.setBrush(QColor(0xF2, 0xA8, 0x2E));
        p.drawRect(QRectF(m.x() + s / 2 - 6, m.y() - s / 2 + 6, 6, s - 6));
    }
    if (scene.clouds) {
        // flat blocky clouds drifting slowly to the right
        for (int i = 0; i < 6; ++i) {
            const qreal block = 8;
            const qreal w = block * (6 + int(4 * (0.5 + 0.5 * hash(i * 11))));
            const qreal span = b.width() + w * 2;
            const qreal x = b.left() - w + std::fmod(t * (6 + 3 * (0.5 + 0.5 * hash(i * 3))) + (0.5 + 0.5 * hash(i * 23)) * span, span) - lx * 14;
            const qreal y = b.top() + 18 + (0.5 + 0.5 * hash(i * 31)) * b.height() * 0.32 - ly * 4;
            p.setBrush(withAlpha(Qt::white, 0.22));
            p.drawRect(QRectF(int(x), int(y), w, block * 2));
            p.drawRect(QRectF(int(x + block * 2), int(y - block), w - block * 4, block));
            p.setBrush(withAlpha(Qt::white, 0.12));
            p.drawRect(QRectF(int(x), int(y + block * 2), w, block * 0.6));
        }
    }
    if (!scene.sun) {
        // pixel moon with a soft halo

        const QPointF m(b.left() + b.width() * 0.62 - lx * 10, b.top() + 52 - ly * 5);
        QRadialGradient halo(m, 70);
        halo.setColorAt(0.0, withAlpha(QColor(0xE8, 0xF0, 0xE0), 0.14));
        halo.setColorAt(1.0, withAlpha(QColor(0xE8, 0xF0, 0xE0), 0.0));
        p.setBrush(halo);
        p.drawEllipse(m, 70, 70);
        const qreal s = 28;
        p.setBrush(QColor(0xE6, 0xEA, 0xDC));
        p.drawRect(QRectF(m.x() - s / 2, m.y() - s / 2, s, s));
        p.setBrush(QColor(0xC9, 0xCE, 0xBF));
        p.drawRect(QRectF(m.x() - 8, m.y() - 8, 6, 6));
        p.drawRect(QRectF(m.x() + 4, m.y() + 1, 6, 6));
        p.drawRect(QRectF(m.x() - 6, m.y() + 6, 4, 4));
        p.setBrush(QColor(0xF6, 0xF8, 0xF0));
        p.drawRect(QRectF(m.x() - s / 2, m.y() - s / 2, s, 3));
    }

    // three ranges of blocky hills; the nearer ones move more with the mouse
    struct Layer {
        qreal block;
        qreal base;    // fraction of the banner height where the hills sit
        qreal amp;     // hill height in blocks
        qreal shift;   // parallax in px
        QColor fill;
        qreal grass;   // alpha of the grass top
        int seed;
    };
    const std::array<Layer, 3> layers = { {
        { 10, 0.72, 6.0, 10, scene.hills[0], scene.grass[0], 11 },
        { 14, 0.86, 5.0, 24, scene.hills[1], scene.grass[1], 37 },
        { 18, 1.00, 4.0, 44, scene.hills[2], scene.grass[2], 71 },
    } };
    for (const Layer& L : layers) {
        const qreal offset = -lx * L.shift;
        const int first = int(std::floor((b.left() - offset) / L.block)) - 1;
        const int last = int(std::ceil((b.right() - offset) / L.block)) + 1;
        for (int c = first; c <= last; ++c) {
            // smooth rolling hills: neighbouring columns differ by at most a block or so
            const qreal n = 0.6 * qSin(c * 0.11 + L.seed) + 0.32 * qSin(c * 0.043 + L.seed * 0.5) + 0.08 * hash(c + L.seed);
            const int rows = qMax(1, int(std::round((n + 1.0) * 0.5 * L.amp)) + 1);
            const qreal x = c * L.block + offset;
            const qreal top = b.top() + b.height() * L.base - rows * L.block - ly * L.shift * 0.2;
            p.setBrush(L.fill);
            p.drawRect(QRectF(x, top, L.block + 0.5, b.bottom() - top));
            p.setBrush(withAlpha(accent, L.grass));
            p.drawRect(QRectF(x, top, L.block + 0.5, L.block * 0.3));
            // a pixel of grass hanging over the edge now and then
            if (hash(c * 3 + L.seed) > 0.2) {
                p.drawRect(QRectF(x + L.block * 0.25, top + L.block * 0.3, L.block * 0.25, L.block * 0.2));
            }
        }
    }

    // a few fireflies drifting over the front hills
    for (int i = 0; i < 14; ++i) {
        const qreal phase = std::fmod(t * (0.03 + 0.02 * (0.5 + 0.5 * hash(i * 5))) + 0.5 + 0.5 * hash(i * 17), 1.0);
        const qreal x = b.left() + phase * b.width() - lx * 30;
        const qreal y = b.top() + b.height() * (0.62 + 0.25 * (0.5 + 0.5 * hash(i * 29))) + qSin(t * 1.3 + i) * 6;
        const qreal a = 0.4 + 0.6 * (0.5 + 0.5 * qSin(t * 2.4 + i * 1.7));
        p.setBrush(withAlpha(i % 4 == 0 ? gold : accentHi, a * 0.25));
        p.drawRect(QRectF(x - 2, y - 2, 6, 6));
        p.setBrush(withAlpha(i % 4 == 0 ? gold : accentHi, a));
        p.drawRect(QRectF(x, y, 2, 2));
    }
}

void YahuHero::paintEvent(QPaintEvent*)
{
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing, true);
    p.setRenderHint(QPainter::SmoothPixmapTransform, true);

    const QRectF banner = bannerRect();
    const qreal t = m_clock.elapsed() / 1000.0;
    const QColor tone = m_running ? gold : accent;
    const QPainterPath shape = rounded(banner, kRadiusCard + 4);

    // soft shadow under the banner
    p.setPen(Qt::NoPen);
    for (int i = 4; i >= 1; --i) {
        p.setBrush(withAlpha(Qt::black, 0.10));
        p.drawPath(rounded(banner.adjusted(-i * 2, -i + 6, i * 2, i * 3 + 6), kRadiusCard + 4 + i * 2));
    }

    p.save();
    p.setClipPath(shape);
    p.setRenderHint(QPainter::Antialiasing, false);  // crisp pixel landscape
    paintLandscape(p, banner, t);
    p.setRenderHint(QPainter::Antialiasing, true);

    // the instance emblem on the right, glowing in the instance's tone
    const QPointF stage(banner.right() - kInnerPad - kOrbit - 24 + m_look.x() * 10, banner.center().y() - 6 + m_look.y() * 5);
    {
        QRadialGradient glow(stage, 170);
        glow.setColorAt(0.0, withAlpha(tone, 0.12));
        glow.setColorAt(1.0, withAlpha(tone, 0.0));
        p.fillRect(banner, glow);
        paintEmblem(p, stage, t);
    }

    // legibility veil on the left
    {
        QLinearGradient veil(banner.topLeft(), banner.topRight());
        // lighter over a sunset, the white text still reads on it
        veil.setColorAt(0.0, withAlpha(ink, scene.sun ? 0.6 : 0.82));
        veil.setColorAt(0.5, withAlpha(ink, scene.sun ? 0.15 : 0.35));
        veil.setColorAt(0.75, withAlpha(ink, 0.0));
        p.fillRect(banner, veil);
    }
    p.restore();

    // block edges: light on top and left, dark at the bottom and right
    p.setRenderHint(QPainter::Antialiasing, false);
    blockEdges(p, banner, kRadiusCard + 4, 4.0);
    p.setRenderHint(QPainter::Antialiasing, true);

    // text block
    {
        const qreal slide = (1.0 - m_swap) * 24.0;
        const qreal left = banner.left() + kInnerPad;
        const qreal textWidth = banner.width() - kInnerPad * 2 - kOrbit * 2 - 60;
        qreal y = banner.top() + 46;

        p.setOpacity(qBound(0.0, m_swap * 1.6, 1.0));

        QFont title = displayFont(26, QFont::Black);
        const QFontMetricsF tm(title);
        p.setFont(title);
        p.setPen(text);
        p.drawText(QPointF(left + slide, y + tm.ascent()), tm.elidedText(m_title, Qt::ElideRight, qMax(80.0, textWidth)));
        y += tm.height() + 2;

        QFont metaFont = font();
        metaFont.setPointSizeF(metaFont.pointSizeF() + 0.5);
        const QFontMetricsF mm(metaFont);
        p.setFont(metaFont);
        p.setPen(muted);
        p.drawText(QPointF(left + slide * 1.5, y + mm.ascent()), mm.elidedText(m_meta, Qt::ElideRight, qMax(80.0, textWidth)));
        p.setOpacity(1.0);
    }
}
