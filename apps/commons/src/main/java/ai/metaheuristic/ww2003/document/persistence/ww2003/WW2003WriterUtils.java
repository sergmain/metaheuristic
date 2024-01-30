/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ww2003.document.persistence.ww2003;

import ai.metaheuristic.ww2003.Enums;
import ai.metaheuristic.ww2003.document.Composite;
import ai.metaheuristic.ww2003.document.ThreadLocalUtils;
import ai.metaheuristic.ww2003.document.exceptions.DocumentProcessingException;
import ai.metaheuristic.ww2003.document.persistence.CommonWriter;
import ai.metaheuristic.ww2003.document.persistence.ww2003.property.WW2003PropertyUtils;
import ai.metaheuristic.ww2003.document.style.InnerStyle;
import ai.metaheuristic.ww2003.document.tags.HasProperty;
import ai.metaheuristic.ww2003.document.tags.Shadowed;
import ai.metaheuristic.ww2003.document.tags.ww2003.*;
import ai.metaheuristic.ww2003.document.tags.xml.*;
import lombok.SneakyThrows;
import javax.annotation.Nullable;

import java.io.Writer;
import java.util.function.Consumer;

/**
 * @author Serge
 * Date: 8/13/2021
 * Time: 1:35 AM
 */
public class WW2003WriterUtils {

    public static final String EMPTY_PARA = """
            <w:p><w:r>
            <w:t></w:t></w:r></w:p>""";

    public static boolean needOpenPara(AbstractWW2003Tag tag) {
        return tag.hasPrev() || (tag.hasParent() && tag.getParent().instanceOfTCell()) /* || (!tag.hasPrev() && tag.hasParent() && tag.getParent().instanceOfField())*/;
    }

    public static boolean needClosePara(AbstractWW2003Tag tag) {
        return tag.hasNext()
               || (tag.hasParent() && tag.getParent().instanceOfTCell())
               || !tag.hasNext()/* && tag.hasParent() && tag.getParent().instanceOfField())*/;
    }

    @SneakyThrows
    public static void printPPropProperties(CommonWriter.Context context, WW2003WritersImpl ww2003Writers, Writer writer, Composite composite) {
        printPPropProperties(context, ww2003Writers, writer, composite, null, Enums.ShadowColorScheme.normal);
    }

    @SneakyThrows
    public static void printPPropProperties(CommonWriter.Context context, WW2003WritersImpl ww2003Writers, Writer writer, Composite composite, @Nullable Integer firstLine) {
        printPPropProperties(context, ww2003Writers, writer, composite, firstLine, Enums.ShadowColorScheme.normal);
    }

    @SneakyThrows
    public static void printPPropProperties(CommonWriter.Context context, WW2003WritersImpl ww2003Writers, Writer writer, Composite composite, @Nullable Integer firstLine,
                                            Enums.ShadowColorScheme colorScheme) {
        printPPropProperties(context, ww2003Writers, writer, composite, firstLine, colorScheme, WW2003WriterUtils::emptyFunc);
    }

    @SneakyThrows
    public static void printPPropProperties(
            CommonWriter.Context context,
            WW2003WritersImpl ww2003Writers, Writer writer, Composite composite, @Nullable Integer firstLine,
            Enums.ShadowColorScheme colorScheme, Consumer<DummyNode> postCreationFunc) {
        try (DummyNode dummyNode = new DummyNode(composite, false, false)) {
            postCreationFunc.accept(dummyNode);
            if (context.insideTable) {
                String style = ThreadLocalUtils.getInnerStyles().findStyleByName(ai.metaheuristic.ww2003.document.Enums.ConsPStyle.CONS_DT_NORMAL.styleName).map(InnerStyle::getStyleId).orElse(ai.metaheuristic.ww2003.document.Enums.ConsPStyle.CONS_DT_NORMAL.styleId);
                PStyle pStyle = new PStyle(Attr.get("w", "val", style));
                WW2003PropertyUtils.addPProp(dummyNode, new PProp(pStyle));
            }
            if (composite instanceof Shadowed shadowed && shadowed.isShadow()) {
                final PProp pProp = WW2003WriterUtils.createShadowedProp(context.insideTable, colorScheme);
                WW2003PropertyUtils.addPProp(dummyNode, pProp);
            }
            if (firstLine!=null) {
                Ind ind = WW2003WriterUtils.createIndentProp(firstLine);
                PProp pProp = new PProp();
                WW2003PropertyUtils.addPProp(dummyNode, pProp);
                dummyNode.addPropertyElement(PProp.class, ind);
            }
            dummyNode.findProperty(PProp.class).ifPresent(n -> {
                WW2003PropertyUtils.sortPropElements(n);
                ww2003Writers.write(context, n, writer);
            });
        }
    }

    @SneakyThrows
    public static void printRPropProperties(CommonWriter.Context context, WW2003WritersImpl ww2003Writers, Writer writer, Composite composite, boolean vanish) {
        printRPropProperties(context, ww2003Writers, writer, composite, vanish, WW2003WriterUtils::emptyFunc);
    }

    private static void emptyFunc(DummyNode n) {
        return;
    }

    @SneakyThrows
    public static void printRPropProperties(CommonWriter.Context context, WW2003WritersImpl ww2003Writers, Writer writer, Composite composite, boolean vanish,
                                            Consumer<DummyNode> postCreationFunc) {
        try (DummyNode dummyNode = new DummyNode(composite, vanish)) {
            postCreationFunc.accept(dummyNode);
            dummyNode.findProperty(RProp.class).ifPresent(n -> {
                WW2003PropertyUtils.sortPropElements(n);
                ww2003Writers.write(context, n, writer);
            });
        }
    }

    @SneakyThrows
    public static void printProperties(CommonWriter.Context context, WW2003WritersImpl ww2003Writers, Writer writer, Composite composite, boolean vanish) {
        try (DummyNode dummyNode = new DummyNode(composite, vanish)) {
            dummyNode.streamProperties().forEach(n -> {
                if (n instanceof HasProperty hasProperty) {
                    WW2003PropertyUtils.sortProps(hasProperty);
                }
                ww2003Writers.write(context, n, writer);
            });
        }
    }

    @SuppressWarnings("ConstantConditions")
    public static Ind createIndentProp(Integer indent) {
        if (indent==null) {
            throw new DocumentProcessingException("(indent!=null)");
        }
        return new Ind(Attr.get("w", "first-line", indent == 0 ? "0" : String.valueOf(indent)));
    }

    public static PProp createShadowedProp(boolean inTable, Enums.ShadowColorScheme colorScheme) {
        String style;
        if (inTable) {
            style = ThreadLocalUtils.getInnerStyles().findStyleByName(ai.metaheuristic.ww2003.document.Enums.ConsPStyle.CONS_DT_NORMAL.styleName).map(InnerStyle::getStyleId).orElse(ai.metaheuristic.ww2003.document.Enums.ConsPStyle.CONS_DT_NORMAL.styleId);
        }
        else {
            style = ThreadLocalUtils.getInnerStyles().findStyleByName(ai.metaheuristic.ww2003.document.Enums.ConsPStyle.CONS_NORMAL.styleName).map(InnerStyle::getStyleId).orElse(ai.metaheuristic.ww2003.document.Enums.ConsPStyle.CONS_NORMAL.styleId);
        }
        PStyle pStyle = new PStyle(Attr.get("w", "val", style));
        String color = switch (colorScheme) {
            case normal -> "EFDEC7";
            case vst -> "EAD5FF";
            default -> throw new DocumentProcessingException("Unexpected value: " + colorScheme);
        };
        Shd shd = new Shd(Attr.get("w", "val", "clear"),
                Attr.get("w", "color", "auto"),
                Attr.get("w", "fill", color));

        return new PProp(pStyle, shd);
    }
}
