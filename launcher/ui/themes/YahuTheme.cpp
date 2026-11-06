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

#include "YahuTheme.h"

#include <QObject>

#include "ui/yahu/YahuStyle.h"

using namespace YahuStyle;

QString YahuTheme::id()
{
    return "yahu";
}

QString YahuTheme::name()
{
    return QObject::tr("Yahu Black");
}

QString YahuTheme::tooltip()
{
    return QObject::tr("The Yahu Client look");
}

QPalette YahuTheme::colorScheme()
{
    QPalette p;
    p.setColor(QPalette::Window, ink);
    p.setColor(QPalette::WindowText, text);
    p.setColor(QPalette::Base, surface);
    p.setColor(QPalette::AlternateBase, raised);
    p.setColor(QPalette::ToolTipBase, raisedHi);
    p.setColor(QPalette::ToolTipText, text);
    p.setColor(QPalette::Text, text);
    p.setColor(QPalette::Button, raised);
    p.setColor(QPalette::ButtonText, text);
    p.setColor(QPalette::BrightText, gold);
    p.setColor(QPalette::Light, lineHi);
    p.setColor(QPalette::Midlight, line);
    p.setColor(QPalette::Mid, line);
    p.setColor(QPalette::Dark, ink);
    p.setColor(QPalette::Shadow, Qt::black);
    p.setColor(QPalette::Link, accentHi);
    p.setColor(QPalette::LinkVisited, accent);
    p.setColor(QPalette::Highlight, accent);
    p.setColor(QPalette::HighlightedText, ink);
    p.setColor(QPalette::PlaceholderText, faint);
    p.setColor(QPalette::Accent, accent);
    return fadeInactive(p, fadeAmount(), fadeColor());
}

double YahuTheme::fadeAmount()
{
    return 0.55;
}

QColor YahuTheme::fadeColor()
{
    return ink;
}

bool YahuTheme::hasStyleSheet()
{
    return true;
}

LogColors YahuTheme::logColorScheme()
{
    LogColors colors = defaultLogColors(colorScheme());
    colors.foreground[MessageLevel::Launcher] = accent;
    colors.foreground[MessageLevel::Debug] = muted;
    colors.foreground[MessageLevel::Warning] = gold;
    colors.foreground[MessageLevel::Error] = danger;
    colors.foreground[MessageLevel::Fatal] = danger;
    return colors;
}

QString YahuTheme::appStyleSheet()
{
    // Tokens are spliced in so the sheet stays in sync with YahuStyle.h.
    QString qss = QStringLiteral(R"QSS(
* { outline: 0; }

QMainWindow, QDialog, QWizard { background: @ink; }
QMainWindow[yahuOverlay="true"], QDialog[yahuOverlay="true"] { background: transparent; }
QMainWindow::separator { background: @line; width: 2px; height: 2px; }

/* Everything is a block: 2px edges, light on top, dark at the bottom, small square-ish corners. */

QToolTip {
    color: @text; background: @surface; border: 2px solid @amethyst;
    border-radius: 0px; padding: 6px 10px;
}

QToolBar { background: transparent; border: none; spacing: 4px; padding: 4px; }
QToolBar QToolButton { border-radius: 3px; padding: 6px; border: 2px solid transparent; color: @muted; }
QToolBar QToolButton:hover { background: @raised; border-color: @lineHi @line @edge @line; color: @text; }
QToolBar QToolButton:checked { background: @raisedHi; border-color: @accent; color: @text; }
QToolBar QToolButton:disabled { color: @faint; }
QToolBar::separator { background: @line; width: 2px; height: 2px; margin: 6px; }

QStatusBar { background: @ink; color: @faint; border-top: 2px solid @line; }
QStatusBar QLabel { color: @faint; padding: 0px 8px; }
QStatusBar::item { border: none; }

/* ---------- Menus ---------- */
QMenuBar { background: @ink; color: @muted; }
QMenuBar::item { padding: 6px 10px; background: transparent; border-radius: 3px; }
QMenuBar::item:selected { background: @raised; color: @text; }
QMenu {
    background: @surface; border: 2px solid @lineHi; border-bottom-color: @edge; border-right-color: @edge;
    border-radius: 3px; padding: 6px; color: @text;
}
QMenu::item { padding: 8px 28px 8px 12px; border-radius: 2px; border-left: 3px solid transparent; }
QMenu::item:selected { background: @raisedHi; color: @text; border-left-color: @accent; }
QMenu::item:disabled { color: @faint; background: transparent; }
QMenu::separator { height: 2px; background: @line; margin: 5px 8px; }
QMenu::icon { padding-left: 8px; }
QMenu::indicator { width: 14px; height: 14px; left: 6px; }
QMenu::right-arrow { image: url(:/yahu/chevron-right.svg); width: 12px; height: 12px; }

/* ---------- Buttons: a block that sinks in when pressed ---------- */
QPushButton {
    background: @raisedHi; color: @text; border: 2px solid @line;
    border-top-color: @lineHi; border-left-color: @lineHi; border-bottom: 4px solid @edge;
    border-radius: 3px; padding: 8px 18px; min-height: 18px; font-weight: 700;
}
QPushButton:hover { background: @lineHi; border-top-color: @muted; border-left-color: @muted; }
QPushButton:pressed { background: @raised; border-bottom-width: 2px; padding-top: 10px; }
QPushButton:disabled { color: @faint; background: @surface; border-color: @surface; border-bottom-color: @surface; }
QPushButton:default {
    background: @accent; color: @ink; border-color: @accentLo; border-top-color: @accentHi; border-left-color: @accentHi;
    border-bottom-color: @accentDeep; font-weight: 800;
}
QPushButton:default:hover { background: @accentHi; }
QPushButton:default:pressed { background: @accentLo; border-bottom-width: 2px; padding-top: 10px; }
QPushButton:default:disabled { background: @raised; color: @faint; border-color: @line; border-bottom-color: @line; }
QPushButton:checked { background: @raisedHi; border-color: @accent; }
QPushButton::menu-indicator { image: url(:/yahu/chevron-down.svg); subcontrol-origin: padding; subcontrol-position: center right; right: 8px; width: 10px; }

QToolButton { color: @text; border-radius: 3px; }
QToolButton:hover { background: @raised; }
QToolButton[popupMode="1"] { padding-right: 16px; }
QToolButton::menu-button { border: none; width: 14px; }
QToolButton::menu-arrow, QToolButton::menu-indicator { image: url(:/yahu/chevron-down.svg); width: 10px; height: 10px; }

QCommandLinkButton {
    background: @raised; border: 2px solid @line; border-top-color: @lineHi; border-bottom: 4px solid @edge;
    border-radius: 3px; color: @text; padding: 12px;
}
QCommandLinkButton:hover { border-color: @sky; background: @raisedHi; }

/* ---------- Inputs: sunk into the surface (dark on top, light at the bottom) ---------- */
QLineEdit, QSpinBox, QDoubleSpinBox, QDateTimeEdit, QKeySequenceEdit {
    background: @ink; color: @text; border: 2px solid @line; border-top-color: @edge; border-left-color: @edge;
    border-radius: 2px; padding: 7px 11px; selection-background-color: @accent; selection-color: @ink;
}
QLineEdit:hover, QSpinBox:hover, QDoubleSpinBox:hover { border-bottom-color: @lineHi; border-right-color: @lineHi; }
QLineEdit:focus, QSpinBox:focus, QDoubleSpinBox:focus, QComboBox:focus { border-color: @sky; }
QLineEdit:disabled, QSpinBox:disabled, QDoubleSpinBox:disabled { color: @faint; }
QSpinBox::up-button, QDoubleSpinBox::up-button, QSpinBox::down-button, QDoubleSpinBox::down-button {
    background: transparent; border: none; width: 18px;
}
QSpinBox::up-arrow, QDoubleSpinBox::up-arrow { image: url(:/yahu/chevron-up.svg); width: 10px; height: 10px; }
QSpinBox::down-arrow, QDoubleSpinBox::down-arrow { image: url(:/yahu/chevron-down.svg); width: 10px; height: 10px; }

QComboBox {
    background: @raisedHi; color: @text; border: 2px solid @line; border-top-color: @lineHi; border-left-color: @lineHi;
    border-bottom: 4px solid @edge; border-radius: 3px; padding: 6px 30px 6px 11px; min-height: 18px;
}
QComboBox:hover { background: @lineHi; }
QComboBox:disabled { color: @faint; }
QComboBox::drop-down { border: none; width: 26px; }
QComboBox::down-arrow { image: url(:/yahu/chevron-down.svg); width: 12px; height: 12px; }
QComboBox QAbstractItemView {
    background: @surface; border: 2px solid @lineHi; border-radius: 0px; padding: 4px;
    selection-background-color: @raisedHi; selection-color: @text; outline: 0;
}

QTextEdit, QPlainTextEdit, QTextBrowser {
    background: @ink; color: @text; border: 2px solid @line; border-top-color: @edge; border-left-color: @edge;
    border-radius: 2px; selection-background-color: @accent; selection-color: @ink;
}

QCheckBox, QRadioButton { color: @text; spacing: 10px; }
QCheckBox:disabled, QRadioButton:disabled { color: @faint; }
QCheckBox::indicator, QGroupBox::indicator, QTreeView::indicator, QListView::indicator, QTableView::indicator {
    width: 16px; height: 16px; border-radius: 0px; border: 2px solid @lineHi; border-top-color: @edge; border-left-color: @edge;
    background: @ink;
}
QCheckBox::indicator:hover, QGroupBox::indicator:hover { border-color: @accent; }
QCheckBox::indicator:checked, QGroupBox::indicator:checked, QTreeView::indicator:checked,
QListView::indicator:checked, QTableView::indicator:checked {
    background: @accent; border-color: @accentLo; border-top-color: @accentHi; border-left-color: @accentHi; image: url(:/yahu/check.svg);
}
QCheckBox::indicator:indeterminate, QTreeView::indicator:indeterminate {
    background: @accent; border-color: @accentLo; image: url(:/yahu/partial.svg);
}
QRadioButton::indicator {
    width: 16px; height: 16px; border-radius: 0px; border: 2px solid @lineHi; border-top-color: @edge; border-left-color: @edge;
    background: @ink;
}
QRadioButton::indicator:hover { border-color: @sky; }
QRadioButton::indicator:checked { background: qradialgradient(cx:0.5, cy:0.5, radius:0.5, fx:0.5, fy:0.5,
    stop:0 @sky, stop:0.55 @sky, stop:0.56 @ink, stop:1 @ink); border-color: @sky; }

QSlider::groove:horizontal { height: 6px; background: @ink; border: 2px solid @line; }
QSlider::sub-page:horizontal { background: @accent; }
QSlider::handle:horizontal {
    background: @raisedHi; width: 12px; margin: -8px 0; border: 2px solid @lineHi; border-bottom-color: @edge; border-right-color: @edge;
}
QSlider::handle:horizontal:hover { background: @accent; }

/* progress fills up in blocks */
QProgressBar {
    background: @ink; border: 2px solid @line; border-top-color: @edge; border-radius: 0px; color: @text;
    text-align: center; min-height: 10px; padding: 1px;
}
QProgressBar::chunk { width: 10px; margin: 1px; background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 @accentHi, stop:0.4 @accent, stop:1 @accentLo); }

/* ---------- Containers: each kind has its own color stripe on top ---------- */
QGroupBox {
    border: 2px solid @line; border-top: 4px solid @sky; border-bottom-color: @edge; border-radius: 3px;
    margin-top: 22px; padding: 16px 12px 12px 12px; background: @surface;
}
QGroupBox::title {
    subcontrol-origin: margin; subcontrol-position: top left; left: 12px; padding: 0 4px;
    color: @sky; font-weight: 800;
}

QTabWidget::pane { border: 2px solid @line; border-top: 4px solid @accent; border-bottom-color: @edge; border-radius: 3px; background: @surface; top: -2px; }
QTabBar { qproperty-drawBase: 0; }
QTabBar::tab {
    background: @raised; color: @muted; padding: 8px 16px; margin: 0px 4px 0px 0px;
    border: 2px solid @line; border-top-color: @lineHi; border-bottom: none; border-radius: 0px; font-weight: 700;
}
QTabBar::tab:hover { color: @text; background: @raisedHi; }
QTabBar::tab:selected { color: @ink; background: @accent; border-color: @accentLo; border-top-color: @accentHi; }
QTabBar::tab:disabled { color: @faint; }

QSplitter::handle { background: @line; }
QSplitter::handle:horizontal { width: 2px; }
QSplitter::handle:vertical { height: 2px; }

QScrollArea { background: transparent; border: none; }
QScrollArea > QWidget > QWidget { background: transparent; }

/* ---------- Item views ---------- */
QAbstractItemView {
    background: @surface; alternate-background-color: @raised; color: @text;
    border: 2px solid @line; border-top-color: @edge; border-left-color: @edge; border-radius: 2px;
    selection-background-color: @raisedHi; selection-color: @text;
}
QAbstractItemView::item { padding: 5px; }
QAbstractItemView::item:hover { background: @raised; }
QAbstractItemView::item:selected { background: @raisedHi; color: @text; border-left: 3px solid @accent; }
QListView::item { border-radius: 0px; }
QTreeView::branch { background: transparent; }
QTreeView::branch:has-children:closed { image: url(:/yahu/chevron-right.svg); }
QTreeView::branch:has-children:open { image: url(:/yahu/chevron-down.svg); }
QHeaderView { background: transparent; border: none; }
QHeaderView::section {
    background: @raised; color: @muted; border: none; border-bottom: 2px solid @edge; border-right: 2px solid @line;
    padding: 8px 10px; font-weight: 700;
}
QHeaderView::section:hover { color: @text; }
QTableCornerButton::section { background: @raised; border: none; }

/* The instance grid sits on the animated backdrop and paints its own cards. */
InstanceView, InstanceView > QWidget { background: transparent; border: none; }

/* Settings and instance page navigation */
PageContainer QListView, PageDialog QListView {
    background: transparent; border: none; border-right: 2px solid @line; border-radius: 0px; padding: 6px 10px 6px 0px;
}
PageContainer QListView::item, PageDialog QListView::item {
    padding: 9px 12px; margin: 1px 0px; color: @muted; border: none; border-left: 4px solid transparent; border-radius: 0px;
}
PageContainer QListView::item:hover { background: @raised; color: @text; border-left-color: @lineHi; }
PageContainer QListView::item:selected { background: @raisedHi; color: @text; border-left-color: @accent; }

/* ---------- Scroll bars: a little stone block ---------- */
QScrollBar:vertical { background: transparent; width: 12px; margin: 2px; }
QScrollBar:horizontal { background: transparent; height: 12px; margin: 2px; }
QScrollBar::handle:vertical {
    background: @lineHi; min-height: 32px; border: 2px solid @lineHi; border-right-color: @edge; border-bottom-color: @edge; margin: 0px 1px;
}
QScrollBar::handle:horizontal {
    background: @lineHi; min-width: 32px; border: 2px solid @lineHi; border-right-color: @edge; border-bottom-color: @edge; margin: 1px 0px;
}
QScrollBar::handle:hover { background: @faint; }
QScrollBar::add-line, QScrollBar::sub-line { width: 0px; height: 0px; }
QScrollBar::add-page, QScrollBar::sub-page { background: transparent; }

/* Yahu pages */
QFrame#yahuPanel {
    background: @surface; border: 2px solid @line; border-top: 4px solid @sky; border-bottom: 4px solid @edge; border-radius: 3px;
}
QFrame#yahuPanel[yahuTone="grass"] { border-top-color: @accent; }
QFrame#yahuPanel[yahuTone="amethyst"] { border-top-color: @amethyst; }
QFrame#yahuPanel[yahuTone="gold"] { border-top-color: @gold; }
QFrame#yahuPanel QLabel { border: none; }

/* buttons that remove something are redstone red */
QPushButton[yahuDanger="true"] {
    background: @redstone; color: @ink; border-color: @redstoneLo; border-top-color: @redstoneHi; border-left-color: @redstoneHi;
    border-bottom-color: @redstoneDeep;
}
QPushButton[yahuDanger="true"]:hover { background: @redstoneHi; }
QPushButton[yahuDanger="true"]:pressed { background: @redstoneLo; }
QPushButton[yahuDanger="true"]:disabled { background: @surface; color: @faint; border-color: @surface; }
QLabel[yahuHint="true"] { color: @muted; }
QListWidget#yahuServerList { background: transparent; border: none; padding: 0px; }

QLabel { background: transparent; }
QLabel:disabled { color: @faint; }
)QSS");

    // longer names first, so "@accentHi" isn't read as "@accent" + "Hi"
    const QList<std::pair<QString, QColor>> tokens = {
        { "@raisedHi", raisedHi },  { "@raised", raised },        { "@surface", surface },    { "@lineHi", lineHi },
        { "@line", line },          { "@accentHi", accentHi },    { "@accentLo", accentLo },  { "@accentDeep", accentLo.darker(150) },
        { "@accent", accent },      { "@muted", muted },          { "@faint", faint },        { "@text", text },
        { "@redstoneHi", redstone.lighter(125) }, { "@redstoneLo", redstone.darker(130) }, { "@redstoneDeep", redstone.darker(220) },
        { "@redstone", redstone },
        { "@gold", gold },          { "@sky", sky },              { "@amethyst", amethyst },  { "@edge", mix(ink, Qt::black, 0.55) },
        { "@ink", ink },
    };
    for (const auto& [token, color] : tokens) {
        qss.replace(token, color.name());
    }
    return qss;
}
