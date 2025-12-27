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

package ai.metaheuristic.ww2003.document.persistence.ww2003.property;

import ai.metaheuristic.ww2003.Enums;
import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.CDNodeUtils;
import ai.metaheuristic.ww2003.document.Composite;
import ai.metaheuristic.ww2003.document.comparator.DocumentComparator;
import ai.metaheuristic.ww2003.document.exceptions.CompareException;
import ai.metaheuristic.ww2003.document.tags.HasProperty;
import ai.metaheuristic.ww2003.document.tags.Property;
import ai.metaheuristic.ww2003.document.tags.PropertyElement;
import ai.metaheuristic.ww2003.document.tags.xml.*;
import javax.annotation.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class WW2003PropertyUtils {

    public static final String MAGENTA_COLOR = "FF00FF";
    public static final String RED_COLOR = "FF0000";
    public static final String BLUE_COLOR = "0000FF";
    public static final String DARK_RED_COLOR = "800000";

    public static void removePropertyElement(Composite composite, Class<? extends Property> propertyClass, Class<? extends PropertyElement> propertyElementClass) {
        composite.findProperty(propertyClass).ifPresent(
                property -> property.findFirst(propertyElementClass).ifPresent(el -> property.asComposite().remove(el))
        );
    }

    public static boolean checkPropertyEquality(HasProperty cdNode1, HasProperty cdNode2) {
        try {
            DocumentComparator.compareProperties(cdNode1, cdNode2);
        } catch (CompareException e) {
            return false;
        }
        return true;
    }

    public static void removeEmptyProperty(HasProperty cdNode, Class<? extends Property> propertyClass) {
        cdNode.findProperty(propertyClass).ifPresent(prop -> {
            if (((Composite) prop).size() == 0) {
                cdNode.removeProperty(propertyClass);
            }
        });
    }

    public static void copyAllProperties(HasProperty from, HasProperty to) {
        CDNodeUtils.copyAlignment(from, to);
        CDNodeUtils.copyIndentation(from, to);
        from.streamProperties()
                .map(CDNode::clone)
                .map(CDNode::asProperty)
                .forEach(to::setProperty);
    }

    public static void setLinkColor(Run run) {
        run.addPropertyElement(RProp.class, createDarkRedColor());
    }

    public static RProp updateRPropHighlight(Run run) {
        RProp targetProp = (RProp) run.findProperty(RProp.class).map(RProp::clone).orElseGet(RProp::new);
        Attr red = Attr.get("w", "val", "red");
        targetProp.findFirst(Highlight.class).ifPresentOrElse(
                highlight -> highlight.updateAttributeByName("val", red),
                () -> targetProp.add(new Highlight(red)));
        return targetProp;
    }

    public static PProp updatePStyleId(Composite run, String styleId) {
        PProp targetProp = (PProp) run.findProperty(PProp.class).map(PProp::clone).orElseGet(PProp::new);
        Attr styleAttr = Attr.get("w", "val", styleId);
        targetProp.findFirst(PStyle.class).ifPresentOrElse(
                highlight -> highlight.updateAttributeByName("val", styleAttr),
                () -> targetProp.add(new PStyle(styleAttr)));
        return targetProp;
    }

    public static void setVanishProperty(Composite composite) {
        composite.setProperty(createVanishRProp());
    }

    public static RProp createVanishRProp() {
        return new RProp(new Vanish(), createDarkRedColor());
    }

    public static Color createDarkRedColor() {
        return new Color(Attr.get("w", "val", DARK_RED_COLOR));
    }

    public static void addVanishRProp(HasProperty hasProperty) {
        hasProperty.addPropertyElement(RProp.class, new Vanish());
        hasProperty.addPropertyElement(RProp.class, createDarkRedColor());
    }

    public static void addPProp(HasProperty hasProperty, PProp ... pProps) {
        for (PProp pProp : pProps) {
            addPProp(hasProperty, pProp);
        }
    }

    public static void addPProp(HasProperty hasProperty, PProp addPProp) {
        hasProperty.findProperty(PProp.class).ifPresentOrElse(pProp -> {
            addPProp.setParent(hasProperty.asComposite());
            addPProp.asStream(PropertyElement.class).forEach(propertyElement -> {
                hasProperty.addPropertyElement(PProp.class, propertyElement);
            });
        }, () -> {
            hasProperty.setProperty(addPProp);
        });
    }

    public static Jc getJc(Enums.Align align) {
        return getJc(align.align);
    }

    public static Jc getJc(String align) {
        return new Jc(Attr.get("w", "val", align));
    }

    public static Attr getAttrFirstLineIndent540() {
        return Attr.get("w", "first-line", "540");
    }

    public static Attr getAttrFirstLineIndentZero() {
        return Attr.get("w", "first-line", "0");
    }

    public static Ind getFirstLineIndentZero() {
        return new Ind(getAttrFirstLineIndentZero());
    }

    public static boolean hasRPropRedColor(Composite run) {
        return run.hasProperty(RProp.class, Color.class, "val", RED_COLOR);
    }
    public static boolean hasRPropBlueColor(Composite run) {
        return run.hasProperty(RProp.class, Color.class, "val", BLUE_COLOR);
    }

    public static boolean hasRPropMagentaColor(Run run) {
        return run.hasProperty(RProp.class, Color.class, "val", MAGENTA_COLOR);
    }

    public static RProp createMagentaColorRProp() {
        return new RProp(createMagentaColor());
    }

    public static Color createRedColor() {
        return new Color(Attr.get("w", "val", RED_COLOR));
    }

    public static RProp createRedColorRProp() {
        return new RProp(createRedColor());
    }

    public static Color createMagentaColor() {
        return new Color(Attr.get("w", "val", MAGENTA_COLOR));
    }

    public static RProp createBlueColorRProp() {
        return new RProp(createBlueColor());
    }

    public static Color createBlueColor() {
        return new Color(Attr.get("w", "val", BLUE_COLOR));
    }

    public static Enums.Align getAlign(@Nullable Jc jc) {
        if (jc != null && jc.attributes != null) {
            String alignment = jc.attributes.stream().filter(a -> "val".equals(a.name) && "w".equals(a.nameSpace)).findFirst().map(attr -> attr.value).stream().findFirst().orElse(null);
            return Enums.Align.to(alignment);
        }
        return Enums.Align.none;
    }

    @Nullable
    public static Integer getIndent(@Nullable Ind ind) {
        if (ind != null && ind.attributes != null) {
            String alignment = ind.attributes.stream().filter(a -> "first-line".equals(a.name) && "w".equals(a.nameSpace)).findFirst().map(attr -> attr.value).stream().findFirst().orElse(null);
            return (alignment != null) ? Integer.valueOf(alignment) : null;
        }
        return null;
    }

    public static List<Property> sortProps(HasProperty hasProperty) {
        return hasProperty.streamProperties()
                .sorted(Comparator.comparing(prop -> prop.getClass().getSimpleName()))
                .peek(property -> property.asComposite().getNodes().sort(Comparator.comparing(element -> element.getClass().getSimpleName())))
                .collect(Collectors.toList());
    }

    public static final Map<Class, Integer> sortPolicy = Map.of(Vanish.class, 1, Color.class, 10, PStyle.class, 15, Ind.class, 30, Shd.class, 40, Jc.class, 50);

    public static void sortPropElements(Property property) {
        property.asComposite().getNodes().sort((o1, o2) -> {
            Class clazz1 = o1.getClass();
            Class clazz2 = o2.getClass();
            int weight1 = sortPolicy.getOrDefault(clazz1, Integer.MAX_VALUE);
            int weight2 = sortPolicy.getOrDefault(clazz2, Integer.MAX_VALUE);
            int compare = Integer.compare(weight1, weight2);
            return compare==0 ? clazz1.getSimpleName().compareTo(clazz2.getSimpleName()) : compare;
        });
    }

}
