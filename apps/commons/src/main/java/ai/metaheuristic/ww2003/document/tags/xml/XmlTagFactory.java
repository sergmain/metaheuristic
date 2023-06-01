/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ww2003.document.tags.xml;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.tags.xml.table.*;

import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class XmlTagFactory {

    private static final Map<String, Supplier<? extends CDNode>> tagMap;

    static {
        tagMap = new HashMap<>();
        tagMap.put("w:p", Para::new);
        tagMap.put("w:tbl", Tbl::new);
        tagMap.put("w:ppr", PProp::new);
        tagMap.put("w:vertalign", VertAlign::new);
        tagMap.put("w:pict", Pict::new);
        tagMap.put("w:t", Text::new);
        tagMap.put("w:r", Run::new);
        tagMap.put("w:rpr", RProp::new);
        tagMap.put("w:br", Br::new);
        tagMap.put("custom", CustomProperty::new);
        tagMap.put("w:position", Position::new);
        tagMap.put("w:i", Italic::new);
        tagMap.put("w:b", Bold::new);
        tagMap.put("w:u", U::new);
        tagMap.put("w:highlight", Highlight::new);
        tagMap.put("w:color", Color::new);
        tagMap.put("w:vanish", Vanish::new);
        tagMap.put("w:pstyle", PStyle::new);
        tagMap.put("w:shd", Shd::new);
        tagMap.put("w:jc", Jc::new);
        tagMap.put("w:ind", Ind::new);
        tagMap.put("w:insideh", InsideH::new);
        tagMap.put("w:insidev", InsideV::new);
        tagMap.put("w:bottom", Bottom::new);
        tagMap.put("w:right", Right::new);
        tagMap.put("w:top", Top::new);
        tagMap.put("w:left", Left::new);
        tagMap.put("w:tblcellmar", TblCellMar::new);
        tagMap.put("w:tblborders", TblBorders::new);
        tagMap.put("w:tblind", TblInd::new);
        tagMap.put("w:tbllayout", TblLayout::new);
        tagMap.put("w:tblprex", TblPropEx::new);
        tagMap.put("w:tblpr", TblProp::new);
        tagMap.put("o:oleobject", OLEObject::new);
        tagMap.put("w:bindata", BinData::new);
        tagMap.put("v:imagedata", ImageData::new);
        tagMap.put("v:shape", Shape::new);
        tagMap.put("w:vmerge", VMerge::new);
        tagMap.put("w:tcw", TCellWidth::new);
        tagMap.put("w:tcpr", TCellProp::new);
        tagMap.put("w:tc", TCell::new);
        tagMap.put("w:trheight", TRowHeight::new);
        tagMap.put("w:trpr", TRowProp::new);
        tagMap.put("w:tr", TRow::new);
        tagMap.put("w:gridcol", GridCol::new);
        tagMap.put("w:tblgrid", TblGrid::new);
        tagMap.put("w:gridspan", GridSpan::new);
        tagMap.put("o:documentproperties", DocumentProperties::new);
        tagMap.put("wx:sect", Sect::new);
        tagMap.put("w:sectpr", SectProp::new);
        tagMap.put("wx:sub-section", SubSection::new);
        tagMap.put("w:valign", VAlign::new);
        tagMap.put("w:tcmar", TCellMar::new);
        tagMap.put("w:tcborders", TCellBorders::new);
        tagMap.put("w:fonts", Fonts::new);
        tagMap.put("w:font", WFont::new);
        tagMap.put("wx:font", WxFont::new);
        tagMap.put("w:styles", Styles::new);
        tagMap.put("w:style", Style::new);
        tagMap.put("w:docpr", DocPr::new);
        tagMap.put("w:body", Body::new);
        tagMap.put("w:name", Name::new);
        tagMap.put("aml:annotation", AmlAnnotation::new);
        tagMap.put("w:instrtext", InstrText::new);
        tagMap.put("w:tblstyle", TblStyle::new);
        tagMap.put("w:tblw", TblWidth::new);
        tagMap.put("w:listpr", ListPr::new);
        tagMap.put("wx:t", WxText::new);
        tagMap.put("w:caps", Caps::new);
        tagMap.put("w:spacing", Spacing::new);
        tagMap.put("w:hlink", HLink::new);
        tagMap.put("w:nobreakhyphen", NoBreakHyphen::new);
        tagMap.put("w:tab", Tab::new);
        tagMap.put("w:sz", Sz::new);
        tagMap.put("w:pgmar", PgMar::new);
        tagMap.put("w:pgsz", PgSize::new);
        tagMap.put("wx:pbdrgroup", PBdrGroup::new);
        tagMap.put("w:rstyle", RStyle::new);
    }

    private XmlTagFactory() {
    }

    public static CDNode createNode(@Nullable String nameSpace, String tagName) {
        String key = S.b(nameSpace) ? tagName : nameSpace + ":" + tagName;
        key = key.toLowerCase();
        Supplier<? extends CDNode> cdNodeSupplier = tagMap.get(key);
        if (cdNodeSupplier == null) {
            return new UnIdentifiedNode(nameSpace, tagName);
        }
        return cdNodeSupplier.get();
    }

}
