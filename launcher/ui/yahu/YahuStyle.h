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

class QPainter;

// Design tokens for the Yahu look: blocky shapes with pixel corners and 3D edges, dark surfaces and lots of
// block colors. The surface and accent tokens follow the chosen palette (KAN-7); applyPalette() sets them once at
// startup, before any window or stylesheet reads them.
namespace YahuStyle {

inline QColor ink{ 0x10, 0x13, 0x1F };       // window background
inline QColor surface{ 0x17, 0x1C, 0x2C };   // panels, navigation rail
inline QColor raised{ 0x1F, 0x26, 0x39 };    // cards, inputs
inline QColor raisedHi{ 0x28, 0x31, 0x49 };  // hovered cards
inline QColor line{ 0x2A, 0x33, 0x50 };      // hairlines
inline QColor lineHi{ 0x3A, 0x46, 0x70 };
inline QColor text{ 0xF7, 0xF8, 0xFC };
inline QColor muted{ 0xB4, 0xBC, 0xD6 };
inline QColor faint{ 0x6E, 0x78, 0x9A };
inline QColor accent{ 0x6C, 0xD8, 0x3E };    // grass
inline QColor accentHi{ 0x9C, 0xF0, 0x6C };
inline QColor accentLo{ 0x3E, 0x9A, 0x22 };
inline const QColor gold{ 0xFF, 0xC9, 0x4A };      // highlights, running games, shop
inline const QColor danger{ 0xFF, 0x5A, 0x5F };

// Block colors for the things that should be told apart at a glance (navigation, panels, tabs).
inline const QColor sky{ 0x55, 0xB4, 0xFF };
inline const QColor amethyst{ 0xB5, 0x83, 0xFF };
inline const QColor copper{ 0xFF, 0x99, 0x45 };
inline const QColor diamond{ 0x4A, 0xE3, 0xD3 };
inline const QColor redstone{ 0xFF, 0x52, 0x52 };
// The block color for the n-th item of a row: grass, sky, gold, amethyst, copper, diamond, redstone, then again.
QColor blockColor(int index);

// A complete set of the tokens above that change with the theme.
struct Palette {
    QString id;    // stored in the "YahuPalette" setting
    QString name;  // shown in the settings
    QColor ink, surface, raised, raisedHi, line, lineHi, text, muted, faint, accent, accentHi, accentLo;
};

// The home banner's landscape for the palette: a sunset over grass and dirt, or a night sky.
struct Scene {
    QColor sky[4];  // top to horizon
    bool sun = false;
    QColor hills[3];  // far to near
    qreal grass[3];   // how strong the grass tops are, far to near
    bool clouds = false;
};
inline Scene scene;

// Every palette the player can pick, the default ("overworld") first.
QList<Palette> palettes();
// Switches the tokens to this palette; an unknown id falls back to the default. Returns the id actually used.
QString applyPalette(const QString& id);

// Corner radii: cards and panels, controls, small chips. rounded() turns them into pixel steps.
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
// The everyday shape: a rectangle whose corners step in like a block in pixel art. The step follows the radius.
QPainterPath rounded(const QRectF& rect, qreal radius);
// The 3D edges of a block on a shape made with rounded(): light at the top and left, dark at the bottom and right.
void blockEdges(QPainter& p, const QRectF& rect, qreal radius, qreal width = 2.0, qreal strength = 1.0);

// Linear interpolation between two colors, t in [0, 1].
QColor mix(const QColor& a, const QColor& b, qreal t);
QColor withAlpha(QColor color, qreal alpha);

}  // namespace YahuStyle
