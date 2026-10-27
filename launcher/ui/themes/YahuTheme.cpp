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
    return QObject::tr("Night Grass");
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
QMainWindow::separator { background: @line; width: 1px; height: 1px; }

QToolTip {
    color: @text; background: @raisedHi; border: 1px solid @accent;
    border-radius: 0px; padding: 6px 9px;
}

QToolBar { background: transparent; border: none; spacing: 4px; padding: 4px; }
QToolBar QToolButton { border-radius: 4px; padding: 6px; border: 1px solid transparent; color: @muted; }
QToolBar QToolButton:hover { background: @raised; border-color: @line; color: @text; }
QToolBar QToolButton:checked { background: @raisedHi; border-color: @accent; color: @text; }
QToolBar QToolButton:disabled { color: @faint; }
QToolBar::separator { background: @line; width: 1px; height: 1px; margin: 6px; }

QStatusBar { background: @ink; color: @faint; border-top: 1px solid @line; }
QStatusBar QLabel { color: @faint; padding: 0px 8px; }
QStatusBar::item { border: none; }

/* ---------- Menus ---------- */
QMenuBar { background: @ink; color: @muted; }
QMenuBar::item { padding: 6px 10px; background: transparent; }
QMenuBar::item:selected { background: @raised; color: @text; }
QMenu {
    background: @surface; border: 1px solid @lineHi; padding: 6px; color: @text;
}
QMenu::item { padding: 8px 28px 8px 12px; border-left: 2px solid transparent; }
QMenu::item:selected { background: @raisedHi; border-left: 2px solid @accent; color: @text; }
QMenu::item:disabled { color: @faint; background: transparent; }
QMenu::separator { height: 1px; background: @line; margin: 5px 4px; }
QMenu::icon { padding-left: 8px; }
QMenu::indicator { width: 14px; height: 14px; left: 6px; }
QMenu::right-arrow { image: url(:/yahu/chevron-right.svg); width: 12px; height: 12px; }

/* ---------- Buttons ---------- */
QPushButton {
    background: @raised; color: @text; border: 1px solid @line; border-bottom: 2px solid @lineHi;
    border-radius: 3px; padding: 7px 18px; min-height: 18px; font-weight: 600;
}
QPushButton:hover { background: @raisedHi; border-color: @lineHi; border-bottom-color: @accent; }
QPushButton:pressed { background: @line; border-bottom-width: 1px; padding-top: 8px; }
QPushButton:disabled { color: @faint; background: @surface; border-color: @surface; }
QPushButton:default { background: @accent; color: @ink; border-color: @accent; border-bottom: 2px solid #3DB34C; }
QPushButton:default:hover { background: @accentHi; border-color: @accentHi; }
QPushButton:checked { background: @raisedHi; border-color: @accent; }
QPushButton::menu-indicator { image: url(:/yahu/chevron-down.svg); subcontrol-origin: padding; subcontrol-position: center right; right: 8px; width: 10px; }

QToolButton { color: @text; border-radius: 3px; }
QToolButton:hover { background: @raised; }
QToolButton[popupMode="1"] { padding-right: 16px; }
QToolButton::menu-button { border: none; width: 14px; }
QToolButton::menu-arrow, QToolButton::menu-indicator { image: url(:/yahu/chevron-down.svg); width: 10px; height: 10px; }

QCommandLinkButton { background: @raised; border: 1px solid @line; border-radius: 3px; color: @text; padding: 10px; }
QCommandLinkButton:hover { border-color: @accent; }

/* ---------- Inputs ---------- */
QLineEdit, QSpinBox, QDoubleSpinBox, QDateTimeEdit, QKeySequenceEdit {
    background: @ink; color: @text; border: 1px solid @line; border-radius: 3px;
    padding: 7px 10px; selection-background-color: @accent; selection-color: @ink;
}
QLineEdit:hover, QSpinBox:hover, QDoubleSpinBox:hover { border-color: @lineHi; }
QLineEdit:focus, QSpinBox:focus, QDoubleSpinBox:focus, QComboBox:focus { border-color: @accent; }
QLineEdit:disabled, QSpinBox:disabled, QDoubleSpinBox:disabled { color: @faint; }
QSpinBox::up-button, QDoubleSpinBox::up-button, QSpinBox::down-button, QDoubleSpinBox::down-button {
    background: transparent; border: none; width: 18px;
}
QSpinBox::up-arrow, QDoubleSpinBox::up-arrow { image: url(:/yahu/chevron-up.svg); width: 10px; height: 10px; }
QSpinBox::down-arrow, QDoubleSpinBox::down-arrow { image: url(:/yahu/chevron-down.svg); width: 10px; height: 10px; }

QComboBox {
    background: @ink; color: @text; border: 1px solid @line; border-radius: 3px;
    padding: 7px 30px 7px 10px; min-height: 18px;
}
QComboBox:hover { border-color: @lineHi; }
QComboBox:disabled { color: @faint; }
QComboBox::drop-down { border: none; width: 26px; }
QComboBox::down-arrow { image: url(:/yahu/chevron-down.svg); width: 12px; height: 12px; }
QComboBox QAbstractItemView {
    background: @surface; border: 1px solid @lineHi; padding: 4px;
    selection-background-color: @raisedHi; selection-color: @text; outline: 0;
}

QTextEdit, QPlainTextEdit, QTextBrowser {
    background: @ink; color: @text; border: 1px solid @line; border-radius: 3px;
    selection-background-color: @accent; selection-color: @ink;
}

QCheckBox, QRadioButton { color: @text; spacing: 10px; }
QCheckBox:disabled, QRadioButton:disabled { color: @faint; }
QCheckBox::indicator, QGroupBox::indicator, QTreeView::indicator, QListView::indicator, QTableView::indicator {
    width: 16px; height: 16px; border-radius: 2px; border: 1px solid @lineHi; background: @ink;
}
QCheckBox::indicator:hover, QGroupBox::indicator:hover { border-color: @accent; }
QCheckBox::indicator:checked, QGroupBox::indicator:checked, QTreeView::indicator:checked,
QListView::indicator:checked, QTableView::indicator:checked {
    background: @accent; border-color: @accent; image: url(:/yahu/check.svg);
}
QCheckBox::indicator:indeterminate, QTreeView::indicator:indeterminate {
    background: @accent; border-color: @accent; image: url(:/yahu/partial.svg);
}
QRadioButton::indicator { width: 16px; height: 16px; border-radius: 9px; border: 1px solid @lineHi; background: @ink; }
QRadioButton::indicator:hover { border-color: @accent; }
QRadioButton::indicator:checked { background: qradialgradient(cx:0.5, cy:0.5, radius:0.5, fx:0.5, fy:0.5,
    stop:0 @accent, stop:0.5 @accent, stop:0.51 @ink, stop:1 @ink); border-color: @accent; }

QSlider::groove:horizontal { height: 4px; background: @line; }
QSlider::sub-page:horizontal { background: @accent; }
QSlider::handle:horizontal { background: @text; width: 12px; height: 12px; margin: -4px 0; }
QSlider::handle:horizontal:hover { background: @accentHi; }

QProgressBar {
    background: @ink; border: 1px solid @line; border-radius: 0px; color: @text;
    text-align: center; min-height: 12px;
}
QProgressBar::chunk { background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #3DB34C, stop:1 @accentHi); }

/* ---------- Containers ---------- */
QGroupBox {
    border: 1px solid @line; border-radius: 3px; margin-top: 20px; padding: 16px 12px 12px 12px;
    background: @surface;
}
QGroupBox::title {
    subcontrol-origin: margin; subcontrol-position: top left; left: 10px; padding: 0 4px;
    color: @accent; font-weight: 700;
}

QTabWidget::pane { border: 1px solid @line; background: @surface; top: -1px; }
QTabBar { qproperty-drawBase: 0; }
QTabBar::tab {
    background: transparent; color: @muted; padding: 9px 16px; margin-right: 2px;
    border: none; border-bottom: 2px solid transparent; font-weight: 600;
}
QTabBar::tab:hover { color: @text; border-bottom: 2px solid @lineHi; }
QTabBar::tab:selected { color: @text; border-bottom: 2px solid @accent; }
QTabBar::tab:disabled { color: @faint; }

QSplitter::handle { background: @line; }
QSplitter::handle:horizontal { width: 1px; }
QSplitter::handle:vertical { height: 1px; }

QScrollArea { background: transparent; border: none; }
QScrollArea > QWidget > QWidget { background: transparent; }

/* ---------- Item views ---------- */
QAbstractItemView {
    background: @surface; alternate-background-color: @raised; color: @text;
    border: 1px solid @line; border-radius: 3px;
    selection-background-color: @raisedHi; selection-color: @text;
}
QAbstractItemView::item { padding: 5px; }
QAbstractItemView::item:hover { background: @raised; }
QAbstractItemView::item:selected { background: @raisedHi; color: @text; }
QListView::item:selected { border-left: 2px solid @accent; }
QTreeView::branch { background: transparent; }
QTreeView::branch:has-children:closed { image: url(:/yahu/chevron-right.svg); }
QTreeView::branch:has-children:open { image: url(:/yahu/chevron-down.svg); }
QHeaderView { background: transparent; border: none; }
QHeaderView::section {
    background: @surface; color: @muted; border: none; border-bottom: 1px solid @line;
    padding: 8px 10px; font-weight: 700;
}
QHeaderView::section:hover { color: @text; }
QTableCornerButton::section { background: @surface; border: none; }

/* The instance grid sits on the animated backdrop and paints its own cards. */
InstanceView, InstanceView > QWidget { background: transparent; border: none; }

/* Settings and instance page navigation */
PageContainer QListView, PageDialog QListView {
    background: transparent; border: none; border-right: 1px solid @line; padding: 6px 8px 6px 0px;
}
PageContainer QListView::item, PageDialog QListView::item {
    padding: 9px 12px; margin: 1px 0px; color: @muted; border: none; border-left: 2px solid transparent;
}
PageContainer QListView::item:hover { background: @raised; color: @text; }
PageContainer QListView::item:selected { background: @raisedHi; color: @text; border-left: 2px solid @accent; }

/* ---------- Scroll bars ---------- */
QScrollBar:vertical { background: transparent; width: 8px; margin: 2px; }
QScrollBar:horizontal { background: transparent; height: 8px; margin: 2px; }
QScrollBar::handle:vertical { background: @lineHi; min-height: 32px; }
QScrollBar::handle:horizontal { background: @lineHi; min-width: 32px; }
QScrollBar::handle:hover { background: @accent; }
QScrollBar::add-line, QScrollBar::sub-line { width: 0px; height: 0px; }
QScrollBar::add-page, QScrollBar::sub-page { background: transparent; }

QLabel { background: transparent; }
QLabel:disabled { color: @faint; }
)QSS");

    const QList<std::pair<QString, QColor>> tokens = {
        { "@raisedHi", raisedHi }, { "@raised", raised }, { "@surface", surface }, { "@lineHi", lineHi },
        { "@line", line },         { "@accentHi", accentHi }, { "@accent", accent }, { "@muted", muted },
        { "@faint", faint },       { "@text", text },         { "@gold", gold },     { "@ink", ink },
    };
    for (const auto& [token, color] : tokens) {
        qss.replace(token, color.name());
    }
    return qss;
}
