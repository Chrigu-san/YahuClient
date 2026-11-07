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
#include <QPainter>

#include <cmath>
#include <iterator>

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
    QPainterPath path;
    if (radius <= 0.5) {
        path.addRect(r);
        return path;
    }
    // two steps per corner, like a rounded block in pixel art: the outer row is in by 2u, the next by u
    const qreal u = qMin(qMax(2.0, std::round(radius / 4.0)), qMin(r.width(), r.height()) / 4.0);
    const qreal L = r.left(), T = r.top(), R = r.right(), B = r.bottom();
    path.moveTo(L, T + 2 * u);
    path.lineTo(L + u, T + 2 * u);
    path.lineTo(L + u, T + u);
    path.lineTo(L + 2 * u, T + u);
    path.lineTo(L + 2 * u, T);
    path.lineTo(R - 2 * u, T);
    path.lineTo(R - 2 * u, T + u);
    path.lineTo(R - u, T + u);
    path.lineTo(R - u, T + 2 * u);
    path.lineTo(R, T + 2 * u);
    path.lineTo(R, B - 2 * u);
    path.lineTo(R - u, B - 2 * u);
    path.lineTo(R - u, B - u);
    path.lineTo(R - 2 * u, B - u);
    path.lineTo(R - 2 * u, B);
    path.lineTo(L + 2 * u, B);
    path.lineTo(L + 2 * u, B - u);
    path.lineTo(L + u, B - u);
    path.lineTo(L + u, B - 2 * u);
    path.lineTo(L, B - 2 * u);
    path.closeSubpath();
    return path;
}

void blockEdges(QPainter& p, const QRectF& r, qreal radius, qreal width, qreal strength)
{
    p.save();
    p.setClipPath(rounded(r, radius), Qt::IntersectClip);
    p.setPen(Qt::NoPen);
    const QColor hi = withAlpha(Qt::white, 0.16 * strength);
    const QColor lo = withAlpha(Qt::black, 0.38 * strength);
    p.fillRect(QRectF(r.left(), r.top(), r.width(), width), hi);
    p.fillRect(QRectF(r.left(), r.top() + width, width, r.height() - 2 * width), hi);
    p.fillRect(QRectF(r.left(), r.bottom() - width * 1.5, r.width(), width * 1.5), lo);
    p.fillRect(QRectF(r.right() - width, r.top() + width, width, r.height() - width * 2.5), lo);
    p.restore();
}

QColor blockColor(int index)
{
    const QColor colors[] = { accent, sky, gold, amethyst, copper, diamond, redstone };
    const int n = int(std::size(colors));
    return colors[((index % n) + n) % n];
}

QList<Palette> palettes()
{
    return {
        // the default: night blue surfaces under bright block colors
        { QStringLiteral("overworld"), QObject::tr("Overworld"), QColor(0x10, 0x13, 0x1F), QColor(0x17, 0x1C, 0x2C),
          QColor(0x1F, 0x26, 0x39), QColor(0x28, 0x31, 0x49), QColor(0x2A, 0x33, 0x50), QColor(0x3A, 0x46, 0x70), QColor(0xF7, 0xF8, 0xFC),
          QColor(0xB4, 0xBC, 0xD6), QColor(0x6E, 0x78, 0x9A), QColor(0x6C, 0xD8, 0x3E), QColor(0x9C, 0xF0, 0x6C), QColor(0x3E, 0x9A, 0x22) },
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

    if (chosen.id == QStringLiteral("overworld")) {
        // sunset: deep blue up high, violet, then orange at the horizon; grass on dirt hills
        scene = { { QColor(0x2A, 0x3C, 0x9C), QColor(0x7A, 0x4F, 0xB8), QColor(0xE8, 0x70, 0x9A), QColor(0xFF, 0xB0, 0x6A) },
                  true,
                  { QColor(0x4B, 0x34, 0x6E), QColor(0x5A, 0x3B, 0x26), QColor(0x33, 0x22, 0x16) },
                  { 0.55, 0.9, 1.0 },
                  true };
    } else {
        // night: almost black, a little of the palette's surface color in the hills
        scene = { { QColor(0x07, 0x08, 0x0B), QColor(0x07, 0x09, 0x09), mix(chosen.ink, chosen.accentLo, 0.12),
                    mix(chosen.ink, chosen.accentLo, 0.08) },
                  false,
                  { mix(chosen.ink, chosen.surface, 0.8), mix(chosen.ink, Qt::black, 0.4), mix(chosen.ink, Qt::black, 0.8) },
                  { 0.12, 0.22, 0.5 },
                  false };
    }
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
