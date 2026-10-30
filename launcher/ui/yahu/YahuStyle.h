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
#include <QPainterPath>
#include <QRectF>
#include <QString>

// Design tokens for the Yahu look: a deep black canvas, soft graphite surfaces and the grass accent.
namespace YahuStyle {

inline const QColor ink{ 0x07, 0x07, 0x09 };       // window background
inline const QColor surface{ 0x0E, 0x0E, 0x11 };   // panels, navigation rail
inline const QColor raised{ 0x15, 0x15, 0x19 };    // cards, inputs
inline const QColor raisedHi{ 0x1D, 0x1D, 0x22 };  // hovered cards
inline const QColor line{ 0x22, 0x22, 0x28 };      // hairlines
inline const QColor lineHi{ 0x33, 0x33, 0x3B };
inline const QColor text{ 0xF4, 0xF5, 0xF2 };
inline const QColor muted{ 0x9E, 0xA0, 0xA6 };
inline const QColor faint{ 0x5C, 0x5E, 0x66 };
inline const QColor accent{ 0x5B, 0xE3, 0x6B };    // grass
inline const QColor accentHi{ 0x86, 0xF2, 0x93 };
inline const QColor accentLo{ 0x2F, 0x9C, 0x3E };
inline const QColor gold{ 0xFF, 0xC9, 0x4A };      // highlights, running games, shop
inline const QColor danger{ 0xFF, 0x5A, 0x5F };

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
