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
