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
#include <QVector3D>
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
constexpr qreal kBlock = 50.0;    // half edge length of the floating block in px
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
        m_spin = (1.0 - m_swap) * 140.0;
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
        m_eyebrow = m_running ? tr("Playing now") : tr("Ready to play");
        m_meta = m_instance->getStatusbarDescription();
        m_icon = APPLICATION->icons()->getIcon(m_instance->iconKey());
    } else if (m_libraryEmpty) {
        m_running = false;
        m_title = tr("Let's get you playing");
        m_eyebrow = tr("Getting started");
        m_meta = tr("Create an instance: pick a Minecraft version, add a mod loader if you like, done.");
        m_icon = QIcon(QStringLiteral(":/yahu/logo.svg"));
    } else {
        m_running = false;
        const auto account = APPLICATION->accounts()->defaultAccount();
        const QString name = account ? account->profileName() : QString();
        m_title = name.isEmpty() ? tr("Welcome back") : tr("Welcome back, %1").arg(name);
        m_eyebrow = tr("Pick an instance");
        m_meta = tr("Click one of your instances below to get it ready.");
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

void YahuHero::paintBlock(QPainter& p, const QPointF& center, qreal size, qreal yaw, qreal pitch)
{
    // a solid grass block, rotated in 3D and projected orthographically
    static const std::array<QVector3D, 8> corners = { QVector3D(-1, -1, -1), QVector3D(1, -1, -1), QVector3D(1, 1, -1), QVector3D(-1, 1, -1),
                                                      QVector3D(-1, -1, 1),  QVector3D(1, -1, 1),  QVector3D(1, 1, 1),  QVector3D(-1, 1, 1) };
    // faces as corner indices; index 0 is the top (y = -1), index 1 the bottom
    static const std::array<std::array<int, 4>, 6> faces = { { { 0, 1, 5, 4 },
                                                               { 3, 7, 6, 2 },
                                                               { 0, 3, 2, 1 },
                                                               { 4, 5, 6, 7 },
                                                               { 0, 4, 7, 3 },
                                                               { 1, 2, 6, 5 } } };
    const qreal cy = qCos(qDegreesToRadians(yaw)), sy = qSin(qDegreesToRadians(yaw));
    const qreal cp = qCos(qDegreesToRadians(pitch)), sp = qSin(qDegreesToRadians(pitch));

    std::array<QVector3D, 8> v;
    for (int i = 0; i < 8; ++i) {
        const QVector3D c = corners[i];
        const qreal x = c.x() * cy + c.z() * sy;
        const qreal z = -c.x() * sy + c.z() * cy;
        const qreal y = c.y() * cp - z * sp;
        const qreal z2 = c.y() * sp + z * cp;
        v[i] = QVector3D(float(x), float(y), float(z2));
    }

    struct Face {
        int index;
        float depth;
    };
    std::array<Face, 6> order;
    for (int f = 0; f < 6; ++f) {
        float depth = 0;
        for (int k : faces[f]) {
            depth += v[k].z();
        }
        order[f] = { f, depth };
    }
    std::sort(order.begin(), order.end(), [](const Face& a, const Face& b) { return a.depth > b.depth; });  // far first

    const QColor dirt(0x6B, 0x4A, 0x2F);
    auto project = [&](int i) { return center + QPointF(v[i].x(), v[i].y()) * size; };
    for (const Face& face : order) {
        if (face.depth > 0) {
            continue;  // back faces are hidden by the solid front
        }
        QPolygonF poly;
        for (int k : faces[face.index]) {
            poly << project(k);
        }
        const bool top = face.index == 0;
        // simple light from above: the top is brightest, sides get darker the more they turn away
        const qreal light = top ? 1.0 : 0.55 + 0.25 * qAbs(qSin(qDegreesToRadians(yaw + face.index * 90.0)));
        p.setPen(QPen(withAlpha(Qt::black, 0.35), 1.0));
        p.setBrush(top ? mix(accentLo, accent, 0.55) : dirt.darker(int(100 / light)));
        p.drawPolygon(poly);
        if (!top && face.index != 1) {
            // the grass overhang: a green band along the side's top edge (corners 0 and 1 of a side are at y = -1)
            QPolygonF band;
            const auto& f = faces[face.index];
            QList<QPointF> upper, lower;
            for (int k : f) {
                (corners[k].y() < 0 ? upper : lower) << project(k);
            }
            if (upper.size() == 2 && lower.size() == 2) {
                // pair each upper corner with the lower corner below it
                if (QLineF(upper[0], lower[0]).length() > QLineF(upper[0], lower[1]).length()) {
                    std::swap(lower[0], lower[1]);
                }
                const QPointF a = upper[0] + (lower[0] - upper[0]) * 0.22;
                const QPointF b = upper[1] + (lower[1] - upper[1]) * 0.22;
                band << upper[0] << upper[1] << b << a;
                p.setPen(Qt::NoPen);
                p.setBrush(mix(accentLo, accent, 0.25 * light));
                p.drawPolygon(band);
                // ragged pixels hanging off the band
                p.setBrush(mix(accentLo, accent, 0.15));
                for (int s = 1; s < 8; s += 2) {
                    const qreal u = s / 8.0;
                    const QPointF pa = a + (b - a) * u;
                    const QPointF down = (lower[0] - upper[0]) * (0.06 + 0.06 * ((s / 2) % 2));
                    p.drawPolygon(QPolygonF() << pa << pa + (b - a) / 8.0 << pa + (b - a) / 8.0 + down << pa + down);
                }
            }
        }
    }
}

void YahuHero::paintLandscape(QPainter& p, const QRectF& b, qreal t)
{
    const qreal lx = m_look.x();
    const qreal ly = m_look.y();

    // sky: black at the top, a green dusk glow behind the hills so their silhouettes read
    QLinearGradient sky(b.topLeft(), b.bottomLeft());
    sky.setColorAt(0.0, QColor(0x07, 0x08, 0x0B));
    sky.setColorAt(0.35, QColor(0x0C, 0x12, 0x10));
    sky.setColorAt(0.7, QColor(0x1E, 0x38, 0x27));
    sky.setColorAt(1.0, QColor(0x1A, 0x31, 0x23));
    p.fillRect(b, sky);
    {
        QRadialGradient dusk(QPointF(b.center().x() - lx * 20, b.bottom()), b.width() * 0.55);
        dusk.setColorAt(0.0, withAlpha(accent, 0.16));
        dusk.setColorAt(1.0, withAlpha(accent, 0.0));
        p.fillRect(b, dusk);
    }

    // stars, twinkling, barely moving
    p.setPen(Qt::NoPen);
    for (int i = 0; i < 70; ++i) {
        const qreal x = b.left() + (0.5 + 0.5 * hash(i * 7 + 1)) * b.width() - lx * 6;
        const qreal y = b.top() + (0.5 + 0.5 * hash(i * 13 + 5)) * b.height() * 0.62 - ly * 3;
        const qreal tw = 0.35 + 0.65 * (0.5 + 0.5 * qSin(t * (1.0 + 0.8 * (0.5 + 0.5 * hash(i))) + i));
        const qreal s = hash(i * 3) > 0.7 ? 2.0 : 1.4;
        p.setBrush(withAlpha(i % 9 == 0 ? gold : text, 0.55 * tw));
        p.drawRect(QRectF(int(x), int(y), s, s));
    }

    // pixel moon with a soft halo
    {
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
        { 10, 0.72, 6.0, 10, QColor(0x10, 0x1F, 0x16), 0.20, 11 },
        { 14, 0.86, 5.0, 24, QColor(0x0A, 0x12, 0x0E), 0.32, 37 },
        { 18, 1.00, 4.0, 44, QColor(0x05, 0x07, 0x06), 0.65, 71 },
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

    // the floating block on the right, glowing in the instance's tone
    const QPointF stage(banner.right() - kInnerPad - kBlock * 2.2 + m_look.x() * 14, banner.center().y() - 6 + m_look.y() * 6);
    {
        QRadialGradient glow(stage, 190);
        glow.setColorAt(0.0, withAlpha(tone, 0.20));
        glow.setColorAt(1.0, withAlpha(tone, 0.0));
        p.fillRect(banner, glow);

        const qreal bob = qSin(t * kTau / 3.6) * 6.0;
        const qreal yaw = std::fmod(t * (m_running ? 70.0 : 16.0), 360.0) + m_spin + m_look.x() * 30.0;
        const qreal pitch = 24.0 - m_look.y() * 12.0;  // positive: looking down onto the grass top
        // shadow on the "ground"
        p.setBrush(withAlpha(Qt::black, 0.35 - bob * 0.015));
        p.drawEllipse(QPointF(stage.x(), stage.y() + kBlock * 1.55), kBlock * 0.9 - bob, kBlock * 0.16);
        paintBlock(p, stage + QPointF(0, 18 + bob), kBlock * 0.58, yaw, pitch);

        // rising pixels above the block
        p.setPen(Qt::NoPen);
        for (int i = 0; i < 9; ++i) {
            const qreal phase = std::fmod(t * 0.35 + i * 0.137, 1.0);
            const qreal x = stage.x() + qSin(i * 2.3 + t) * kBlock * 0.8;
            const qreal y = stage.y() - 10 - phase * 120;
            p.setBrush(withAlpha(i % 3 == 0 ? gold : accentHi, (1.0 - phase) * 0.8));
            p.drawRect(QRectF(x, y, 3, 3));
        }

        // the instance icon hovers above the block, leaning with the cursor
        const QPointF iconCenter = stage + QPointF(m_look.x() * 8, -kBlock * 1.15 + bob + m_look.y() * 5);
        const qreal iconSize = 64 + (1.0 - m_swap) * -20;
        p.save();
        p.translate(iconCenter);
        QTransform tilt;
        tilt.rotate(m_look.x() * 14, Qt::YAxis);
        tilt.rotate(-m_look.y() * 10, Qt::XAxis);
        p.setTransform(tilt, true);
        p.setOpacity(qBound(0.0, m_swap * 1.5, 1.0));
        m_icon.paint(&p, QRect(int(-iconSize / 2), int(-iconSize / 2), int(iconSize), int(iconSize)));
        p.restore();
    }

    // legibility veil on the left
    {
        QLinearGradient veil(banner.topLeft(), banner.topRight());
        veil.setColorAt(0.0, withAlpha(ink, 0.82));
        veil.setColorAt(0.5, withAlpha(ink, 0.35));
        veil.setColorAt(0.75, withAlpha(ink, 0.0));
        p.fillRect(banner, veil);
    }
    p.restore();

    // hairline border with a light top edge
    p.setBrush(Qt::NoBrush);
    p.setPen(QPen(withAlpha(text, 0.08), 1.0));
    p.drawPath(rounded(banner.adjusted(0.5, 0.5, -0.5, -0.5), kRadiusCard + 4));

    // text block
    {
        const qreal slide = (1.0 - m_swap) * 24.0;
        const qreal left = banner.left() + kInnerPad;
        const qreal textWidth = banner.width() - kInnerPad * 2 - kBlock * 4.4;
        qreal y = banner.top() + 30;

        p.setOpacity(qBound(0.0, m_swap * 1.6, 1.0));

        // eyebrow chip: a status dot and pixel text on a translucent pill
        QFont eyebrow = pixelFont(8);
        const QFontMetricsF em(eyebrow);
        const QRectF chip(left + slide * 0.4, y, em.horizontalAdvance(m_eyebrow) + 30, 22);
        p.setPen(Qt::NoPen);
        p.setBrush(withAlpha(tone, 0.12));
        p.drawRoundedRect(chip, 11, 11);
        const qreal pulse = 0.5 + 0.5 * qSin(t * kTau / 1.6);
        p.setBrush(withAlpha(tone, 0.3 * pulse));
        p.drawEllipse(QPointF(chip.left() + 12, chip.center().y()), 5, 5);
        p.setBrush(tone);
        p.drawEllipse(QPointF(chip.left() + 12, chip.center().y()), 3, 3);
        p.setFont(eyebrow);
        p.setPen(tone);
        p.drawText(QRectF(chip.left() + 21, chip.top(), chip.width() - 21, chip.height()), Qt::AlignVCenter | Qt::AlignLeft, m_eyebrow);
        y += chip.height() + 14;

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
