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

#include "YahuSheetHost.h"

#include <QApplication>
#include <QDialog>
#include <QEvent>
#include <QKeyEvent>
#include <QLayout>
#include <QTimer>
#include <QMainWindow>
#include <QPainter>
#include <QRegularExpression>
#include <QStyle>
#include <QToolButton>
#include <QVariantAnimation>
#include <QWidget>

#include <algorithm>

#include "YahuStyle.h"

using namespace YahuStyle;

QPointer<YahuSheetHost> YahuSheetHost::s_active;

namespace {
constexpr int kPageHeader = 60;
constexpr int kCardHeader = 54;
constexpr int kCardPad = 16;
constexpr qreal kCut = 14.0;
constexpr const char* kOverlayProperty = "yahuOverlay";

QString cleanTitle(QWidget* w)
{
    QString title = w->windowTitle();
    if (title.trimmed().isEmpty()) {
        return QApplication::applicationDisplayName();
    }
    // drop the " — Yahu Client" style suffixes windows put in their titles
    title.remove(QRegularExpression(QStringLiteral("\\s+[-—–]\\s+Yahu Client.*$")));
    return title;
}
}  // namespace

YahuSheetHost::YahuSheetHost(QWidget* mainWindow) : QObject(mainWindow), m_window(mainWindow)
{
    mainWindow->installEventFilter(this);
    s_active = this;
}

YahuSheetHost* YahuSheetHost::active()
{
    if (s_active && s_active->m_window->isVisible() && !s_active->m_window->isMinimized()) {
        return s_active;
    }
    return nullptr;
}

bool YahuSheetHost::wantsToAdopt(QWidget* w)
{
    if (!w->isWindow() || !active() || w == active()->m_window || w->property(kOverlayProperty).toBool()) {
        return false;
    }
    if (w->windowType() != Qt::Window && w->windowType() != Qt::Dialog) {
        return false;
    }
    // native platform dialogs and the first-run wizard keep being real windows
    for (const char* name : { "QFileDialog", "QColorDialog", "QFontDialog", "QWizard" }) {
        if (w->inherits(name)) {
            return false;
        }
    }
    return qobject_cast<QDialog*>(w) || qobject_cast<QMainWindow*>(w);
}

bool YahuSheetHost::coversWindow() const
{
    for (auto it = m_sheets.cbegin(); it != m_sheets.cend(); ++it) {
        if (it.value().page && it.value().enter >= 1.0 && it.key()->isVisible()) {
            return true;
        }
    }
    return false;
}

void YahuSheetHost::adopt(QWidget* w)
{
    if (m_sheets.contains(w)) {
        return;
    }
    m_sheets.insert(w, Sheet{});

    // a frameless, see-through window owned by the main window: it can't end up behind it or in the taskbar
    w->setProperty(kOverlayProperty, true);
    w->setAttribute(Qt::WA_TranslucentBackground, true);
    const Qt::WindowFlags flags = (w->windowFlags() & Qt::WindowType_Mask) | Qt::FramelessWindowHint;
    w->setParent(w->parentWidget() ? w->parentWidget() : m_window, flags);
    w->winId();  // make sure the native window exists again before Qt continues showing it
    w->style()->unpolish(w);
    w->style()->polish(w);

    auto* close = new QToolButton(w);
    close->setCursor(Qt::PointingHandCursor);
    close->setAutoRaise(true);
    close->setFocusPolicy(Qt::NoFocus);
    close->setToolTip(tr("Close (Esc)"));
    close->setStyleSheet(QStringLiteral("QToolButton { color: %1; background: %2; border: 1px solid %3; padding: 6px 12px; font-weight: 700; }"
                                        "QToolButton:hover { color: %4; border-color: %5; background: %6; }")
                             .arg(muted.name(), raised.name(), line.name(), text.name(), accent.name(), raisedHi.name()));
    connect(close, &QToolButton::clicked, w, &QWidget::close);
    m_sheets[w].closeButton = close;

    w->installEventFilter(this);
    connect(w, &QObject::destroyed, this, [this, w] { m_sheets.remove(w); });
}

QRect YahuSheetHost::overlayRect() const
{
    // the main window's client area in global coordinates
    return m_window->geometry();
}

QSize YahuSheetHost::contentNeed(QWidget* w)
{
    // the layout's own wish, without the margins we add around it for the chrome
    if (auto* layout = w->layout()) {
        return layout->sizeHint().expandedTo(layout->minimumSize());
    }
    return w->sizeHint();
}

void YahuSheetHost::updateCard(QWidget* w)
{
    const Sheet& sheet = m_sheets[w];
    const QRect area = overlayRect();
    QSize size(sheet.natural.width() + kCardPad * 2, sheet.natural.height() + kCardHeader + kCardPad);
    size = size.expandedTo(QSize(400, 180)).boundedTo(QSize(int(area.width() * 0.9), int(area.height() * 0.9)));
    QRect card(QPoint(0, 0), size);
    card.moveCenter(QRect(QPoint(0, 0), area.size()).center());
    w->setProperty("_yahu_card", card);
}

void YahuSheetHost::place(QWidget* w)
{
    Sheet& sheet = m_sheets[w];
    const QRect area = overlayRect();
    m_placing = true;
    // dialogs that pin their own size (fixed-size layouts, setFixedSize) would refuse to cover the window
    if (auto* layout = w->layout(); layout && layout->sizeConstraint() != QLayout::SetDefaultConstraint) {
        layout->setSizeConstraint(QLayout::SetDefaultConstraint);
    }
    w->setMinimumSize(0, 0);
    w->setMaximumSize(QWIDGETSIZE_MAX, QWIDGETSIZE_MAX);
    if (w->geometry() != area) {
        w->setGeometry(area);
    }

    const int slide = int((1.0 - sheet.enter) * (sheet.page ? 30 : 22));
    auto* close = static_cast<QToolButton*>(sheet.closeButton);
    if (sheet.page) {
        w->setContentsMargins(24, kPageHeader + 8 + slide, 24, 12 - slide);
        close->setText(tr("←  Back"));
        close->adjustSize();
        close->move(28, (kPageHeader - close->height()) / 2 + slide);
    } else {
        const QRect card = w->property("_yahu_card").toRect();
        w->setContentsMargins(card.left() + kCardPad, card.top() + kCardHeader + slide, area.width() - card.right() + kCardPad,
                              area.height() - card.bottom() + kCardPad - slide);
        close->setText(QStringLiteral("✕"));
        close->adjustSize();
        close->move(card.right() - close->width() - 12, card.top() + (kCardHeader - close->height()) / 2 + slide);
    }
    close->raise();
    close->show();
    m_placing = false;
}

void YahuSheetHost::appear(QWidget* w)
{
    Sheet& sheet = m_sheets[w];
    const QRect area = overlayRect();

    // first appearance: decide between a full page and a card from the window's natural size
    if (!sheet.natural.isValid()) {
        // what the content really needs, never less than what the window chose for itself before we got it
        sheet.natural = contentNeed(w).expandedTo(w->size()).expandedTo(w->minimumSizeHint());
        sheet.page = qobject_cast<QMainWindow*>(w) || w->inherits("PageDialog") || w->inherits("NewInstanceDialog") ||
                     w->inherits("ResourceDownloadDialog") || sheet.natural.width() > area.width() * 0.62 ||
                     sheet.natural.height() > area.height() * 0.72;
    }
    if (!sheet.page) {
        updateCard(w);
    }

    sheet.enter = 0.0;
    place(w);

    auto* anim = new QVariantAnimation(w);
    anim->setStartValue(0.0);
    anim->setEndValue(1.0);
    anim->setDuration(sheet.page ? 320 : 260);
    anim->setEasingCurve(QEasingCurve::OutCubic);
    connect(anim, &QVariantAnimation::valueChanged, w, [this, w](const QVariant& v) {
        if (!m_sheets.contains(w)) {
            return;
        }
        m_sheets[w].enter = v.toReal();
        place(w);
        w->update();
    });
    anim->start(QAbstractAnimation::DeleteWhenStopped);
}

void YahuSheetHost::paintChrome(QWidget* w)
{
    const Sheet& sheet = m_sheets[w];
    QPainter p(w);
    p.setRenderHint(QPainter::Antialiasing, true);
    const QRect r = w->rect();
    const int slide = int((1.0 - sheet.enter) * (sheet.page ? 30 : 22));

    if (sheet.page) {
        p.fillRect(r, ink);
        p.setPen(QPen(line, 1.0));
        p.drawLine(QPointF(0, kPageHeader + slide + 0.5), QPointF(r.width(), kPageHeader + slide + 0.5));
        p.fillRect(QRectF(28, kPageHeader + slide - 1, 52, 2), accent);
        p.setFont(displayFont(15, QFont::Bold));
        p.setPen(text);
        const int titleX = 28 + sheet.closeButton->width() + 18;
        p.drawText(QRect(titleX, slide, r.width() - titleX - 28, kPageHeader), Qt::AlignVCenter | Qt::AlignLeft, cleanTitle(w));
        return;
    }

    p.fillRect(r, withAlpha(QColor(2, 6, 3), 0.62 * sheet.enter));
    const QRectF card = QRectF(w->property("_yahu_card").toRect()).translated(0, slide).adjusted(0.5, 0.5, -0.5, -0.5);
    const QPainterPath shape = chamfer(card, kCut);
    // soft glow under the card
    p.setPen(Qt::NoPen);
    for (int i = 6; i >= 1; --i) {
        p.setBrush(withAlpha(accent, 0.012 * (7 - i)));
        p.drawPath(chamfer(card.adjusted(-i * 2, -i * 2, i * 2, i * 2), kCut + i * 2));
    }
    p.fillPath(shape, surface);
    p.setPen(QPen(lineHi, 1.0));
    p.drawPath(shape);
    // lit corner, top left
    p.setPen(QPen(accent, 2.0, Qt::SolidLine, Qt::SquareCap, Qt::MiterJoin));
    p.drawPolyline(QPolygonF() << QPointF(card.left() + 1, card.top() + kCut + 16) << QPointF(card.left() + 1, card.top() + kCut)
                               << QPointF(card.left() + kCut, card.top() + 1) << QPointF(card.left() + kCut + 16, card.top() + 1));
    p.setFont(displayFont(11, QFont::Bold));
    p.setPen(text);
    p.drawText(QRectF(card.left() + 22, card.top(), card.width() - 90, kCardHeader), Qt::AlignVCenter | Qt::AlignLeft,
               QFontMetrics(p.font()).elidedText(cleanTitle(w), Qt::ElideRight, int(card.width() - 90)));
    p.setPen(QPen(line, 1.0));
    p.drawLine(QPointF(card.left() + kCardPad, card.top() + kCardHeader - 6), QPointF(card.right() - kCardPad, card.top() + kCardHeader - 6));
}

bool YahuSheetHost::eventFilter(QObject* watched, QEvent* event)
{
    if (watched == m_window) {
        if (event->type() == QEvent::Move || event->type() == QEvent::Resize) {
            for (auto it = m_sheets.cbegin(); it != m_sheets.cend(); ++it) {
                if (it.key()->isVisible()) {
                    if (!it.value().page) {
                        updateCard(it.key());
                    }
                    place(it.key());
                }
            }
        }
        return false;
    }

    auto* w = qobject_cast<QWidget*>(watched);
    if (!w || !m_sheets.contains(w)) {
        return false;
    }
    switch (event->type()) {
        case QEvent::Show:
            appear(w);
            break;
        case QEvent::Hide:
            // When an overlay closes while another app has focus (e.g. the browser after signing in), the launcher
            // could stay half inactive and swallow clicks. Hand focus and the mouse back to it explicitly.
            if (w->isHidden()) {
                QTimer::singleShot(0, m_window, [this] {
                    if (auto* grabber = QWidget::mouseGrabber()) {
                        grabber->releaseMouse();
                    }
                    if (!QApplication::activeModalWidget() && m_window->isVisible()) {
                        m_window->setEnabled(true);
                        m_window->raise();
                        m_window->activateWindow();
                    }
                });
            }
            break;
        case QEvent::Paint:
            paintChrome(w);
            break;
        case QEvent::WindowTitleChange:
            w->update();
            break;
        case QEvent::Move:
        case QEvent::Resize:
            // some windows resize or re-center themselves while open (progress dialogs do); pull them back in
            if (!m_placing && w->isVisible() && w->geometry() != overlayRect()) {
                QTimer::singleShot(0, w, [this, w] {
                    if (m_sheets.contains(w) && w->isVisible()) {
                        place(w);
                    }
                });
            }
            break;
        case QEvent::LayoutRequest:
            // a card grows with its content (e.g. a progress dialog gaining a second line)
            if (!m_sheets[w].page && w->isVisible()) {
                const QSize need = contentNeed(w);
                if (need.width() > m_sheets[w].natural.width() || need.height() > m_sheets[w].natural.height()) {
                    m_sheets[w].natural = m_sheets[w].natural.expandedTo(need);
                    updateCard(w);
                    place(w);
                    w->update();
                }
            }
            break;
        case QEvent::KeyPress:
            // QMainWindows don't close on Esc by themselves
            if (static_cast<QKeyEvent*>(event)->key() == Qt::Key_Escape && qobject_cast<QMainWindow*>(w)) {
                w->close();
                return true;
            }
            break;
        default:
            break;
    }
    return false;
}
