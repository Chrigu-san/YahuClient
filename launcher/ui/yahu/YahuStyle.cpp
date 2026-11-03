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

#include "YahuStyle.h"

#include <QApplication>
#include <QFontDatabase>

namespace YahuStyle {

void loadFonts()
{
    QFontDatabase::addApplicationFont(":/yahu/fonts/Unbounded.ttf");
    QFontDatabase::addApplicationFont(":/yahu/fonts/Silkscreen-Regular.ttf");
    QFontDatabase::addApplicationFont(":/yahu/fonts/InstrumentSans.ttf");

    if (QFontDatabase::hasFamily(uiFamily)) {
        QFont font(uiFamily);
        font.setPointSizeF(QApplication::font().pointSizeF() + 0.5);
        font.setHintingPreference(QFont::PreferNoHinting);
        QApplication::setFont(font);
    }
}

QFont displayFont(qreal pointSize, QFont::Weight weight)
{
    QFont font(QFontDatabase::hasFamily(displayFamily) ? displayFamily : QApplication::font().family());
    font.setPointSizeF(pointSize);
    font.setWeight(weight);
    return font;
}

QFont pixelFont(int pixelSize)
{
    QFont font(QFontDatabase::hasFamily(pixelFamily) ? pixelFamily : QApplication::font().family());
    font.setPixelSize(pixelSize);
    font.setCapitalization(QFont::AllUppercase);
    font.setLetterSpacing(QFont::AbsoluteSpacing, 1.0);
    font.setStyleStrategy(QFont::NoAntialias);
    font.setHintingPreference(QFont::PreferFullHinting);
    return font;
}

QPainterPath chamfer(const QRectF& r, qreal cut)
{
    cut = qMin(cut, qMin(r.width(), r.height()) / 2.0);
    QPainterPath path;
    path.moveTo(r.left() + cut, r.top());
    path.lineTo(r.right() - cut, r.top());
    path.lineTo(r.right(), r.top() + cut);
    path.lineTo(r.right(), r.bottom() - cut);
    path.lineTo(r.right() - cut, r.bottom());
    path.lineTo(r.left() + cut, r.bottom());
    path.lineTo(r.left(), r.bottom() - cut);
    path.lineTo(r.left(), r.top() + cut);
    path.closeSubpath();
    return path;
}

QPainterPath rounded(const QRectF& r, qreal radius)
{
    radius = qMin(radius, qMin(r.width(), r.height()) / 2.0);
    QPainterPath path;
    path.addRoundedRect(r, radius, radius);
    return path;
}

QList<Palette> palettes()
{
    return {
        { QStringLiteral("black"), QObject::tr("Black"), QColor(0x03, 0x03, 0x04), QColor(0x09, 0x09, 0x0B), QColor(0x10, 0x10, 0x13),
          QColor(0x17, 0x17, 0x1B), QColor(0x1A, 0x1A, 0x1F), QColor(0x2A, 0x2A, 0x31), QColor(0xF4, 0xF5, 0xF2), QColor(0x9E, 0xA0, 0xA6),
          QColor(0x5C, 0x5E, 0x66), QColor(0x5B, 0xE3, 0x6B), QColor(0x86, 0xF2, 0x93), QColor(0x2F, 0x9C, 0x3E) },
        // the first Yahu look: dark moss instead of graphite
        { QStringLiteral("night-grass"), QObject::tr("Night Grass"), QColor(0x0A, 0x0F, 0x0B), QColor(0x12, 0x1A, 0x14),
          QColor(0x18, 0x23, 0x1A), QColor(0x20, 0x2E, 0x23), QColor(0x24, 0x33, 0x29), QColor(0x34, 0x48, 0x3A), QColor(0xEC, 0xF3, 0xEA),
          QColor(0x9D, 0xB0, 0xA2), QColor(0x5F, 0x72, 0x66), QColor(0x5B, 0xE3, 0x6B), QColor(0x86, 0xF2, 0x93), QColor(0x2F, 0x9C, 0x3E) },
        // deep blue surfaces with a cyan accent
        { QStringLiteral("ocean"), QObject::tr("Ocean"), QColor(0x03, 0x05, 0x08), QColor(0x08, 0x0C, 0x12), QColor(0x0E, 0x14, 0x1C),
          QColor(0x15, 0x1D, 0x27), QColor(0x18, 0x21, 0x2C), QColor(0x26, 0x33, 0x42), QColor(0xF2, 0xF5, 0xF8), QColor(0x9C, 0xA6, 0xB2),
          QColor(0x5A, 0x66, 0x74), QColor(0x4F, 0xC3, 0xF7), QColor(0x86, 0xD9, 0xFF), QColor(0x1F, 0x88, 0xC0) },
    };
}

QString applyPalette(const QString& id)
{
    const auto all = palettes();
    Palette chosen = all.first();
    for (const auto& p : all) {
        if (p.id == id) {
            chosen = p;
        }
    }
    ink = chosen.ink;
    surface = chosen.surface;
    raised = chosen.raised;
    raisedHi = chosen.raisedHi;
    line = chosen.line;
    lineHi = chosen.lineHi;
    text = chosen.text;
    muted = chosen.muted;
    faint = chosen.faint;
    accent = chosen.accent;
    accentHi = chosen.accentHi;
    accentLo = chosen.accentLo;
    return chosen.id;
}

QColor mix(const QColor& a, const QColor& b, qreal t)
{
    t = qBound(0.0, t, 1.0);
    return QColor::fromRgbF(a.redF() + (b.redF() - a.redF()) * t, a.greenF() + (b.greenF() - a.greenF()) * t,
                            a.blueF() + (b.blueF() - a.blueF()) * t, a.alphaF() + (b.alphaF() - a.alphaF()) * t);
}

QColor withAlpha(QColor color, qreal alpha)
{
    color.setAlphaF(qBound(0.0, alpha, 1.0));
    return color;
}

}  // namespace YahuStyle
