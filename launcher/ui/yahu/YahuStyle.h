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

// Design tokens for the "Night Grass" look shared by the theme and every custom-painted Yahu widget.
namespace YahuStyle {

inline const QColor ink{ 0x0A, 0x0F, 0x0B };       // window background
inline const QColor surface{ 0x12, 0x1A, 0x14 };   // panels
inline const QColor raised{ 0x18, 0x23, 0x1A };    // cards, inputs
inline const QColor raisedHi{ 0x20, 0x2E, 0x23 };  // hovered cards
inline const QColor line{ 0x24, 0x33, 0x29 };      // hairlines
inline const QColor lineHi{ 0x34, 0x48, 0x3A };
inline const QColor text{ 0xEC, 0xF3, 0xEA };
inline const QColor muted{ 0x9D, 0xB0, 0xA2 };
inline const QColor faint{ 0x5F, 0x72, 0x66 };
inline const QColor accent{ 0x5B, 0xE3, 0x6B };    // grass
inline const QColor accentHi{ 0x86, 0xF2, 0x93 };
inline const QColor gold{ 0xFF, 0xC9, 0x4A };      // highlights, running games
inline const QColor danger{ 0xFF, 0x5A, 0x5F };

inline const QString displayFamily = QStringLiteral("Unbounded");
inline const QString pixelFamily = QStringLiteral("Silkscreen");
inline const QString uiFamily = QStringLiteral("Instrument Sans");

// Registers the bundled fonts and makes the UI font the application default.
void loadFonts();

QFont displayFont(qreal pointSize, QFont::Weight weight = QFont::Bold);
// Tiny pixel-font label used for eyebrows, badges and metadata. Pixel sizes stay on the font's 8px grid.
QFont pixelFont(int pixelSize = 8);

// A rectangle with its corners cut off at 45 degrees: the signature Yahu shape.
QPainterPath chamfer(const QRectF& rect, qreal cut);

// Linear interpolation between two colors, t in [0, 1].
QColor mix(const QColor& a, const QColor& b, qreal t);
QColor withAlpha(QColor color, qreal alpha);

}  // namespace YahuStyle
