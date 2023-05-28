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

package ai.metaheuristic.ww2003.document.style;

import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.WW2003Document;
import ai.metaheuristic.ww2003.document.Enums;
import ai.metaheuristic.ww2003.document.ThreadLocalUtils;
import ai.metaheuristic.ww2003.document.tags.xml.*;

import java.util.List;

import static ai.metaheuristic.ww2003.document.Enums.ConsPStyle.CONS_DT_NORMAL;
import static ai.metaheuristic.ww2003.document.Enums.ConsPStyle.CONS_NORMAL;

/**
 * @author Sergio Lissner
 * Date: 9/9/2022
 * Time: 3:43 AM
 */
public class WW2003DocumentStylesUtils {

    private static final String W_STYLE_CLOSE = "</w:style>";

    public static List<UnIdentifiedRawTextNode> createRawStyles(String text) {
        return text.lines().map(WW2003DocumentStylesUtils::createRawStyle).toList();
    }

    private static UnIdentifiedRawTextNode createRawStyle(String line) {
        String s = line.strip();
        if (!s.startsWith("<w:style")) {
            throw new IllegalStateException("(!s.startsWith('<w:style'))");
        }
        // <w:style w:type="paragraph" w:styleId="20">
        String type = extractValue(s, "w:type");
        String id = extractValue(s, "w:styleId");
        int idx = s.indexOf('>', 1);
        String trim1 = s.substring(idx+1);

        idx = trim1.indexOf(W_STYLE_CLOSE);
        if (idx==-1) {
            throw new IllegalStateException("(idx==-1)");
        }
        String trim2 = trim1.substring(0, idx);
        return WW2003DocumentStylesUtils.createRawStyle(type, id, trim2);
    }

    public static String extractValue(String s, String attr) {
        final String str = attr + "=\"";
        int idx = s.indexOf(str);
        if (idx==-1) {
            throw new IllegalStateException("(idx==-1)");
        }
        final int actualOffset = idx + str.length();
        int nextIdx = s.indexOf('\"', actualOffset + 1);
        if (nextIdx==-1) {
            throw new IllegalStateException("(nextIdx==-1)");
        }
        return s.substring(actualOffset, nextIdx);
    }

    public static UnIdentifiedRawTextNode createRawStyle(String type, String id, String text) {
        UnIdentifiedRawTextNode n = new UnIdentifiedRawTextNode("w", "style");
        n.addAttribute(Attr.get("w", "type", type));
        n.addAttribute(Attr.get("w", "styleId", id));
        n.setText(text);
        return n;
    }

    public static Styles createDefaultStyles() {
        return createDefaultStyles(false);
    }

    public static Styles createDefaultStyles(boolean extendedList) {
        Style styleA = WW2003DocumentBaseStyles.createStyleA();
        Style styleA0 = WW2003DocumentBaseStyles.createStyleA0();
        Style style1 = WW2003DocumentBaseStyles.createStyle1();
        Style style2 = WW2003DocumentBaseStyles.createStyle2();
        Style style3 = WW2003DocumentBaseStyles.createStyle3();
        Style style4 = WW2003DocumentBaseStyles.createStyle4();
        return new Styles(styleA, styleA0, style1, style2, style3, style4);
    }

    public static void setNormalParagraphStyle(List<CDNode> nodes) {
        setStyle(nodes, CONS_NORMAL, CONS_NORMAL.styleId);
    }

    public static void setNormalTableStyle(List<CDNode> nodes) {
        setStyle(nodes, CONS_DT_NORMAL, CONS_DT_NORMAL.styleId);
    }

    @SuppressWarnings("CodeBlock2Expr")
    private static void setStyle(List<CDNode> nodes, Enums.ConsPStyle value, String defaultStyleId) {
        if (nodes.isEmpty()) {
            return;
        }
        String styleId = nodes.get(0).findOuterNode(WW2003Document.class)
                .flatMap(ww2003Document -> ThreadLocalUtils.getInnerStyles().findStyleByName(value.styleName))
                .map(InnerStyle::getStyleId)
                .orElse(defaultStyleId);
        nodes.stream().filter(node -> node.instanceOfPara()).forEach(node -> {
            if (node.asHasProperty().hasProperty(PProp.class, PStyle.class)) {
                return;
            }
            PStyle pStyle = new PStyle(Attr.get("w", "val", styleId));
            node.asHasProperty().findProperty(PProp.class).ifPresentOrElse(pProp -> {
                pProp.insert(0, pStyle);
            }, () -> {
                node.asHasProperty().setProperty(new PProp(pStyle));
            });
        });
    }
}
