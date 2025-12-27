/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ww2003.document.style;

import ai.metaheuristic.ww2003.document.Enums;
import ai.metaheuristic.ww2003.document.persistence.ww2003.property.WW2003PropertyUtils;
import ai.metaheuristic.ww2003.document.tags.xml.*;

/**
 * @author Sergio Lissner
 * Date: 9/9/2022
 * Time: 3:37 AM
 */
public class WW2003DocumentBaseStyles {
    static Style createStyleA() {
        Name name = new Name(Attr.get("w", "val", "Normal"));
        UnIdentifiedNode uiName = new UnIdentifiedNode("wx", "uiName", Attr.get("wx", "val", "Normal"));
        WxFont wxFont = new WxFont();
        wxFont.addAttribute(Attr.get("wx", "val", "Courier New"));
        UnIdentifiedNode lang = new UnIdentifiedNode("w", "lang", Attr.get("w", "val", "RU"), Attr.get("w", "bidi", "AR-SA"));
        RProp rProp = new RProp(wxFont, lang);
        Style style = new Style(name, uiName);
        style.setProperty(rProp);
        style.addAttribute(Attr.get("w", "type", "paragraph"));
        style.addAttribute(Attr.get("w", "styleId", "a"));
        return style;
    }

    static Style createStyleA0() {
        Name name = new Name(Attr.get("w", "val", "Default Paragraph Font"));
        UnIdentifiedNode uiName = new UnIdentifiedNode("wx", "uiName", Attr.get("wx", "val", "Default Paragraph Font"));
        UnIdentifiedNode semiHidden = new UnIdentifiedNode("w", "semiHidden");
        Style style = new Style(name, uiName, semiHidden);
        style.addAttribute(Attr.get("w", "type", "character"));
        style.addAttribute(Attr.get("w", "default", "on"));
        style.addAttribute(Attr.get("w", "styleId", "a0"));
        return style;
    }

    static Style createStyle1() {
        Name name = new Name(Attr.get("w", "val", "ConsNormal"));
        WxFont wxFont = new WxFont();
        wxFont.addAttribute(Attr.get("wx", "val", "Courier New"));
        UnIdentifiedNode sz = new UnIdentifiedNode("w", "sz", Attr.get("w", "val", "20"));
        UnIdentifiedNode lang = new UnIdentifiedNode("w", "lang", Attr.get("w", "val", "RU"), Attr.get("w", "bidi", "AR-SA"));
        RProp rProp = new RProp(wxFont, sz, lang);
        Style style = new Style(name);

        PProp pProp = new PProp( new PStyle(Attr.get("w", "val", "1")));
        style.setProperty(pProp, false);
        pProp.add(WW2003PropertyUtils.getJc("both"));
        pProp.add(new Ind(Attr.get("w", "right", "19772"), WW2003PropertyUtils.getAttrFirstLineIndent540()));

        style.setProperty(rProp);
        style.addAttribute(Attr.get("w", "type", "paragraph"));
        style.addAttribute(Attr.get("w", "default", "on"));
        style.addAttribute(Attr.get("w", "styleId", "1"));
        return style;
    }

    static Style createStyle2() {
        Name name = new Name(Attr.get("w", "val", "ConsNonformat"));
        WxFont wxFont = new WxFont();
        wxFont.addAttribute(Attr.get("wx", "val", "Courier New"));
        UnIdentifiedNode sz = new UnIdentifiedNode("w", "sz", Attr.get("w", "val", "20"));
        UnIdentifiedNode lang = new UnIdentifiedNode("w", "lang", Attr.get("w", "val", "RU"), Attr.get("w", "bidi", "AR-SA"));
        RProp rProp = new RProp(wxFont, sz, lang);
        Style style = new Style(name);

        PProp pProp = new PProp(
                new PStyle(Attr.get("w", "val", "2")),
                new Ind(Attr.get("w", "right", "0"))
        );
        style.setProperty(pProp, false);
        pProp.add(WW2003PropertyUtils.getJc("both"));

        style.setProperty(rProp);
        style.addAttribute(Attr.get("w", "type", "paragraph"));
        style.addAttribute(Attr.get("w", "styleId", "2"));
        return style;
    }

    public static Style createStyle3() {
        Name name = new Name(Attr.get("w", "val", Enums.ConsPStyle.CONS_DT_NORMAL.styleName));
        UnIdentifiedNode rFonts = new UnIdentifiedNode("w", "rFonts");
        rFonts.addAttribute(Attr.get("w", "ascii", "Times New Roman"));
        rFonts.addAttribute(Attr.get("w", "h-ansi", "Times New Roman"));
        rFonts.addAttribute(Attr.get("w", "cs", "Times New Roman"));
        UnIdentifiedNode sz = new UnIdentifiedNode("w", "sz", Attr.get("w", "val", "24"));
        UnIdentifiedNode lang = new UnIdentifiedNode("w", "lang", Attr.get("w", "val", "RU"), Attr.get("w", "bidi", "AR-SA"));
        RProp rProp = new RProp(rFonts, sz, lang);
        Style style = new Style(name);
        final PStyle pStyle = new PStyle(Attr.get("w", "val", "3"));
        PProp pProp = new PProp(pStyle);
        style.setProperty(pProp, false);
        pProp.add(WW2003PropertyUtils.getJc("both"));
        pProp.add(new Ind(Attr.get("w", "right", "0"), WW2003PropertyUtils.getAttrFirstLineIndentZero()));

        style.setProperty(rProp);
        style.addAttribute(Attr.get("w", "type", "paragraph"));
        style.addAttribute(Attr.get("w", "styleId", Enums.ConsPStyle.CONS_DT_NORMAL.styleId));
        return style;
    }

    static Style createStyle4() {
        Name name = new Name(Attr.get("w", "val", "ConsDTNonformat"));
        WxFont wxFont = new WxFont();
        wxFont.addAttribute(Attr.get("wx", "val", "Courier New"));
        UnIdentifiedNode sz = new UnIdentifiedNode("w", "sz", Attr.get("w", "val", "22"));
        UnIdentifiedNode lang = new UnIdentifiedNode("w", "lang", Attr.get("w", "val", "RU"), Attr.get("w", "bidi", "AR-SA"));
        RProp rProp = new RProp(wxFont, sz, lang);
        Style style = new Style(name, rProp);
        PProp pProp = new PProp(
                new PStyle(Attr.get("w", "val", "4")),
                new Ind(Attr.get("w", "right", "0"))
        );
        style.setProperty(pProp, false);
        pProp.add(WW2003PropertyUtils.getJc("both"));

        style.addAttribute(Attr.get("w", "type", "paragraph"));
        style.addAttribute(Attr.get("w", "styleId", "4"));
        return style;
    }
}
