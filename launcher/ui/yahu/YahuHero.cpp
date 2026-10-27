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
#include "YahuPlayButton.h"
#include "YahuStyle.h"
#include "InstanceList.h"
#include "icons/IconList.h"

using namespace YahuStyle;

namespace {
constexpr int kHeight = 268;
constexpr int kSidePad = 36;
constexpr qreal kBlock = 62.0;  // half edge length of the block in px
constexpr qreal kTau = 6.283185307179586;
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
    // the text block is painted; the buttons sit underneath it
    layout->setContentsMargins(kSidePad - 12, 0, kSidePad, 18);
    layout->addStretch(1);
    layout->addLayout(buttons);

    setStyleSheet(QStringLiteral(R"QSS(
        QToolButton[yahuGhost="true"] {
            background: %1; color: %2; border: 1px solid %3; border-bottom: 2px solid %4;
            border-radius: 0px; padding: 0px 14px; min-height: 42px; font-weight: 700;
        }
        QToolButton[yahuGhost="true"]:hover { border-color: %5; background: %6; }
        QToolButton[yahuGhost="true"]:pressed { border-bottom-width: 1px; padding-top: 1px; }
        QToolButton[yahuGhost="true"]:disabled { color: %7; border-color: %3; }
        QToolButton#yahuStop { color: %8; border-color: %8; }
    )QSS")
                      .arg(withAlpha(raised, 0.85).name(QColor::HexArgb), text.name(), line.name(), lineHi.name(), accent.name(),
                           raisedHi.name(), faint.name(), danger.name()));

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
        m_look += (m_lookTarget - m_look) * 0.08;
        update(QRect(width() / 2, 0, width() / 2, height()));
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
        m_title = tr("Welcome back");
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
            // the block looks towards the cursor wherever it is in the window
            const QPointF local = mapFromGlobal(static_cast<QMouseEvent*>(event)->globalPosition());
            const QPointF stage(width() - kSidePad - kBlock * 1.6, height() / 2.0 - 8);
            m_lookTarget = QPointF(qBound(-1.0, (local.x() - stage.x()) / 500.0, 1.0), qBound(-1.0, (local.y() - stage.y()) / 300.0, 1.0));
        }
    }
    return false;
}

void YahuHero::paintBlock(QPainter& p, const QPointF& center, qreal size, qreal yaw, qreal pitch)
{
    // a wireframe-and-glass grass block, rotated in 3D and projected orthographically
    static const std::array<QVector3D, 8> corners = { QVector3D(-1, -1, -1), QVector3D(1, -1, -1), QVector3D(1, 1, -1), QVector3D(-1, 1, -1),
                                                      QVector3D(-1, -1, 1),  QVector3D(1, -1, 1),  QVector3D(1, 1, 1),  QVector3D(-1, 1, 1) };
    // faces as corner indices; index 0 is the top (y = -1)
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

    auto project = [&](int i) { return center + QPointF(v[i].x(), v[i].y()) * size; };
    for (const Face& face : order) {
        QPolygonF poly;
        for (int k : faces[face.index]) {
            poly << project(k);
        }
        const bool top = face.index == 0;
        const bool front = face.depth < 0;
        QColor fill = top ? accent : QColor(0x2A, 0x3D, 0x2E);
        fill.setAlphaF(front ? (top ? 0.42 : 0.30) : 0.10);
        p.setBrush(fill);
        p.setPen(QPen(withAlpha(top ? accentHi : accent, front ? 0.9 : 0.25), front ? 1.6 : 1.0));
        p.drawPolygon(poly);
        if (top && front) {
            // a pixel grass fringe along the top edges
            p.setPen(Qt::NoPen);
            p.setBrush(withAlpha(accentHi, 0.8));
            for (int k = 0; k < 4; ++k) {
                const QPointF a = poly[k];
                const QPointF b = poly[(k + 1) % 4];
                for (int s = 1; s < 6; s += 2) {
                    const QPointF pt = a + (b - a) * (s / 6.0);
                    p.drawRect(QRectF(pt.x() - 1.5, pt.y() - 1.5, 3, 3));
                }
            }
        }
    }
}

void YahuHero::paintEvent(QPaintEvent*)
{
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing, true);
    p.setRenderHint(QPainter::SmoothPixmapTransform, true);

    const QRectF r = rect();
    const qreal t = m_clock.elapsed() / 1000.0;
    const QColor tone = m_running ? gold : accent;

    // legibility veil on the left, fading into the meadow
    {
        QLinearGradient veil(r.topLeft(), r.topRight());
        veil.setColorAt(0.0, withAlpha(ink, 0.75));
        veil.setColorAt(0.55, withAlpha(ink, 0.25));
        veil.setColorAt(1.0, withAlpha(ink, 0.0));
        p.fillRect(r, veil);
    }

    // stage: block + floating icon
    const QPointF stage(r.width() - kSidePad - kBlock * 1.6, r.height() / 2.0 - 8);
    {
        QRadialGradient glow(stage, 210);
        glow.setColorAt(0.0, withAlpha(tone, 0.16));
        glow.setColorAt(1.0, withAlpha(tone, 0.0));
        p.fillRect(r, glow);

        const qreal yaw = std::fmod(t * (m_running ? 70.0 : 18.0), 360.0) + m_spin + m_look.x() * 35.0;
        const qreal pitch = -24.0 + m_look.y() * 16.0;
        const qreal bob = qSin(t * kTau / 3.6) * 5.0;
        paintBlock(p, stage + QPointF(0, 26 + bob * 0.4), kBlock * 0.62, yaw, pitch);

        // rising pixels above the block
        p.setPen(Qt::NoPen);
        for (int i = 0; i < 9; ++i) {
            const qreal phase = std::fmod(t * 0.35 + i * 0.137, 1.0);
            const qreal x = stage.x() + qSin(i * 2.3 + t) * kBlock * 0.8;
            const qreal y = stage.y() + 10 - phase * 140;
            p.setBrush(withAlpha(i % 3 == 0 ? gold : accentHi, (1.0 - phase) * 0.8));
            p.drawRect(QRectF(x, y, 3, 3));
        }

        // the instance icon floats in front, leaning with the cursor
        const QPointF iconCenter = stage + QPointF(m_look.x() * 10, -kBlock * 0.55 + bob + m_look.y() * 6);
        const qreal iconSize = 96 + (1.0 - m_swap) * -30;
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

    // text block on the left
    {
        const qreal slide = (1.0 - m_swap) * 24.0;
        const qreal textWidth = r.width() - kSidePad * 2 - kBlock * 3.4;
        qreal y = 40;

        p.setOpacity(qBound(0.0, m_swap * 1.6, 1.0));
        QFont eyebrow = pixelFont(8);
        p.setFont(eyebrow);
        const QFontMetricsF em(eyebrow);
        // blinking pixel cursor before the eyebrow
        const bool blink = std::fmod(t, 1.1) < 0.7;
        p.setPen(Qt::NoPen);
        p.setBrush(withAlpha(tone, blink ? 1.0 : 0.25));
        p.drawRect(QRectF(kSidePad + slide * 0.4, y + 1, 6, 6));
        p.setPen(tone);
        p.drawText(QPointF(kSidePad + 14 + slide * 0.4, y + 8), m_eyebrow);
        y += em.height() + 12;

        QFont title = displayFont(24, QFont::Black);
        const QFontMetricsF tm(title);
        p.setFont(title);
        p.setPen(text);
        p.drawText(QPointF(kSidePad + slide, y + tm.ascent()), tm.elidedText(m_title, Qt::ElideRight, qMax(80.0, textWidth)));
        y += tm.height() + 4;

        QFont metaFont = font();
        metaFont.setPointSizeF(metaFont.pointSizeF() + 0.5);
        const QFontMetricsF mm(metaFont);
        p.setFont(metaFont);
        p.setPen(muted);
        p.drawText(QPointF(kSidePad + slide * 1.5, y + mm.ascent()), mm.elidedText(m_meta, Qt::ElideRight, qMax(80.0, textWidth)));
        p.setOpacity(1.0);
    }
}
