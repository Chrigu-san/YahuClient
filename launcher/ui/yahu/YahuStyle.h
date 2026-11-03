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

#include <QColor>
#include <QFont>
#include <QList>
#include <QPainterPath>
#include <QRectF>
#include <QString>

// Design tokens for the Yahu look: a deep black canvas, soft graphite surfaces and the grass accent.
// The surface and accent tokens follow the chosen palette (KAN-7); applyPalette() sets them once at startup,
// before any window or stylesheet reads them.
namespace YahuStyle {

inline QColor ink{ 0x03, 0x03, 0x04 };       // window background
inline QColor surface{ 0x09, 0x09, 0x0B };   // panels, navigation rail
inline QColor raised{ 0x10, 0x10, 0x13 };    // cards, inputs
inline QColor raisedHi{ 0x17, 0x17, 0x1B };  // hovered cards
inline QColor line{ 0x1A, 0x1A, 0x1F };      // hairlines
inline QColor lineHi{ 0x2A, 0x2A, 0x31 };
inline QColor text{ 0xF4, 0xF5, 0xF2 };
inline QColor muted{ 0x9E, 0xA0, 0xA6 };
inline QColor faint{ 0x5C, 0x5E, 0x66 };
inline QColor accent{ 0x5B, 0xE3, 0x6B };    // grass
inline QColor accentHi{ 0x86, 0xF2, 0x93 };
inline QColor accentLo{ 0x2F, 0x9C, 0x3E };
inline const QColor gold{ 0xFF, 0xC9, 0x4A };      // highlights, running games, shop
inline const QColor danger{ 0xFF, 0x5A, 0x5F };

// A complete set of the tokens above that change with the theme.
struct Palette {
    QString id;    // stored in the "YahuPalette" setting
    QString name;  // shown in the settings
    QColor ink, surface, raised, raisedHi, line, lineHi, text, muted, faint, accent, accentHi, accentLo;
};

// Every palette the player can pick, the default ("black") first.
QList<Palette> palettes();
// Switches the tokens to this palette; an unknown id falls back to the default. Returns the id actually used.
QString applyPalette(const QString& id);

// Corner radii: cards and panels, controls, small chips.
constexpr qreal kRadiusCard = 14.0;
constexpr qreal kRadiusControl = 10.0;
constexpr qreal kRadiusChip = 6.0;

inline const QString displayFamily = QStringLiteral("Unbounded");
inline const QString pixelFamily = QStringLiteral("Silkscreen");
inline const QString uiFamily = QStringLiteral("Instrument Sans");

// Registers the bundled fonts and makes the UI font the application default.
void loadFonts();

QFont displayFont(qreal pointSize, QFont::Weight weight = QFont::Bold);
// Tiny pixel-font label used for eyebrows, badges and metadata. Pixel sizes stay on the font's 8px grid.
QFont pixelFont(int pixelSize = 8);

// A rectangle with its corners cut off at 45 degrees, used for small pixel accents.
QPainterPath chamfer(const QRectF& rect, qreal cut);
// The everyday shape: a rounded rectangle.
QPainterPath rounded(const QRectF& rect, qreal radius);

// Linear interpolation between two colors, t in [0, 1].
QColor mix(const QColor& a, const QColor& b, qreal t);
QColor withAlpha(QColor color, qreal alpha);

}  // namespace YahuStyle
