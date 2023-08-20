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

package ai.metaheuristic.ww2003.document.comparator;

import ai.metaheuristic.ww2003.document.AbstractCDNode;
import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.Composite;
import ai.metaheuristic.ww2003.document.exceptions.CompareException;
import ai.metaheuristic.ww2003.document.presentation.HighlightColor;
import ai.metaheuristic.ww2003.document.tags.Indentation;
import ai.metaheuristic.ww2003.document.tags.Property;
import ai.metaheuristic.ww2003.document.tags.xml.*;
import ai.metaheuristic.ww2003.document.tags.xml.table.Bottom;
import ai.metaheuristic.ww2003.document.tags.xml.table.TCellBorders;
import ai.metaheuristic.ww2003.document.tags.xml.table.TblPropEx;
import javax.annotation.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class DocumentComparator {

    public static void compare(@Nullable CDNode node1, @Nullable CDNode node2) {
        if (node1==null && node2==null) {
            return;
        }
        if (node1==null || node2==null) {
            throw new CompareException("#1 Nodes are different. ref type: " + (node1!=null ? node1.getClass().getSimpleName(): "null" ) +
                                       ", actual type: " + (node2!=null ? node2.getClass().getSimpleName() : "null"));
        }
        compareTypes(node1, node2);
        compareFields(node1, node2);
        if (node1.instanceOfComposite() && node2.instanceOfComposite()) {
            Composite composite1 = node1.asComposite();
            Composite composite2 = node2.asComposite();
            AtomicInteger size1 = new AtomicInteger(composite1.size());
            AtomicInteger size2 = new AtomicInteger(composite2.size());

            fixTCellBorders_presentInRef(composite1, composite2, size1, size2);
            fixBottom_presentInResult(composite1, composite2, size1, size2);
            fixPStyle(composite1, composite2, size1, size2);
            fixPStyleShadow(composite1, composite2, size1, size2);
            fixPStyleShadowInResult(composite1, composite2, size1, size2);
            fixShadow(composite1, composite2, size1, size2);
            fixShadow1(composite1, composite2, size1, size2);
            fixShadow2(composite1, composite2);

            compareProperties(node1, node2);
            compareAttributes(node1, node2);

            if (size1.get()!=size2.get()) {
                if (size1.get()!=size2.get()+1
                    || composite1.asStream(TCellBorders.class).findFirst().filter(o -> o.getNodes().size()==1).filter(o -> o.getNodes().get(0) instanceof Bottom).isEmpty()
                    || composite2.asStream(TCellBorders.class).findFirst().isPresent()) {
                    Object[] args = new Object[]{size1.get(), size2.get()};
                    throw new CompareException(ai.metaheuristic.commons.S.f("Nodes have different number of sub-nodes, ref count: %d, actual count: %d ", args));
                }
            }
            List<CDNode> nodes1 = composite1.getNodes().stream().sorted(Comparator.comparing(o -> o.getClass().getSimpleName())).toList();
            List<CDNode> nodes2 = composite2.getNodes().stream().sorted(Comparator.comparing(o -> o.getClass().getSimpleName())).toList();
            for (int i = 0; i < size1.get(); i++) {
                CDNode subNode1 = nodes1.get(i);
                CDNode subNode2 = nodes2.get(i);
                compare(subNode1, subNode2);
            }
        }
        else {
            compareProperties(node1, node2);
            compareAttributes(node1, node2);

            if (!node1.getClass().getSimpleName().equals(node2.getClass().getSimpleName())) {
                throw new CompareException(ai.metaheuristic.commons.S.f("Nodes have different types, ref: %s, actual: %s", node1.getClass().getSimpleName(), node2.getClass().getSimpleName()));
            }
        }
    }

    private static void fixPStyleShadow(Composite composite1, Composite composite2, AtomicInteger size1, AtomicInteger size2) {
        if (size1.get()+1==size2.get()) {
            final Optional<Shd> shadow = composite2.asStream(Shd.class).findFirst();
            if (shadow.isPresent() && composite1.asStream(Shd.class).findFirst().isEmpty()) {
                composite1.add(shadow.get().clone());
                size1.incrementAndGet();
            }
        }
        // fix of situation when in 'origin' there isn't Shd nor PStyle properties
        else if (size1.get()+2==size2.get()) {
            final Optional<Shd> shadow = composite2.asStream(Shd.class).findFirst();
            final boolean isShadow = shadow.isPresent() && composite1.asStream(Shd.class).findFirst().isEmpty();
            final Optional<PStyle> pStyle = composite2.asStream(PStyle.class).findFirst();
            final boolean isPStyle = pStyle.isPresent() && composite1.asStream(PStyle.class).findFirst().isEmpty();
            if (isShadow && isPStyle) {
                composite1.add(pStyle.get().clone());
                composite1.add(shadow.get().clone());
                size1.addAndGet(2);
            }
        }
    }
    private static void fixPStyleShadowInResult(Composite composite1, Composite composite2, AtomicInteger size1, AtomicInteger size2) {
        if (!(composite1 instanceof PProp)) {
            return;
        }
        if (!(composite2 instanceof PProp)) {
            return;
        }
        //  fix of situation when in 'origin' there isn't Shd  property
        if (size1.get()==2 && size1.get()==size2.get()+1) {
            final boolean bothPStyle  = composite1.asStream(PStyle.class).findFirst().isPresent() && composite2.asStream(PStyle.class).findFirst().isPresent();
            final Optional<Shd> shadow = composite1.asStream(Shd.class).findFirst();
            if (bothPStyle && shadow.isPresent() && composite2.asStream(Shd.class).findFirst().isEmpty()) {
                composite2.add(shadow.get().clone());
                size2.incrementAndGet();
            }
        }
    }

    public static final Attr blackColorAttr = Attr.get("w", "val", "#000000");

    private static void fixShadow(Composite composite1, Composite composite2, AtomicInteger size1, AtomicInteger size2) {
        if (!(composite1 instanceof RProp)) {
            return;
        }
        if (!(composite2 instanceof RProp)) {
            return;
        }
        if (size1.get()==3 && size1.get()==size2.get()+1) {
            final Optional<Shd> shadow = composite1.asStream(Shd.class).findFirst();
            final boolean isShadow = shadow.isPresent() && composite2.asStream(Shd.class).findFirst().isEmpty();
            final boolean bothVanish  = composite1.asStream(Vanish.class).findFirst().isPresent() && composite2.asStream(Vanish.class).findFirst().isPresent();

            Attr colorAttr1 = composite1.asStream(Color.class).findFirst().map(value -> value.findAttributeByName("val").orElse(blackColorAttr)).orElse(blackColorAttr);
            final boolean isColor1  = HighlightColor.MAROON==HighlightColor.asHighlightColor(colorAttr1.value);

            Attr colorAttr2 = composite2.asStream(Color.class).findFirst().map(value -> value.findAttributeByName("val").orElse(blackColorAttr)).orElse(blackColorAttr);
            final boolean isColor2  = HighlightColor.MAROON==HighlightColor.asHighlightColor(colorAttr2.value);

            if (isShadow && bothVanish && isColor1 && isColor2) {
                composite2.add(shadow.get().clone());
                size2.incrementAndGet();
            }
        }
    }

    private static void fixShadow1(Composite composite1, Composite composite2, AtomicInteger size1, AtomicInteger size2) {
        if (!(composite1 instanceof RProp)) {
            return;
        }
        if (!(composite2 instanceof RProp)) {
            return;
        }

        if (size1.get()==2 && size1.get()==size2.get()+1) {
            final Optional<Shd> shadow = composite1.asStream(Shd.class).findFirst();
            final boolean isShadow = shadow.isPresent() && composite2.asStream(Shd.class).findFirst().isEmpty();

            Attr colorAttr1 = composite1.asStream(Color.class).findFirst().map(value -> value.findAttributeByName("val").orElse(blackColorAttr)).orElse(blackColorAttr);
            final boolean isColor1  = HighlightColor.FUCHSIA==HighlightColor.asHighlightColor(colorAttr1.value);

            Attr colorAttr2 = composite2.asStream(Color.class).findFirst().map(value -> value.findAttributeByName("val").orElse(blackColorAttr)).orElse(blackColorAttr);
            final boolean isColor2  = HighlightColor.FUCHSIA==HighlightColor.asHighlightColor(colorAttr2.value);

            if (isShadow && isColor1 && isColor2) {
                composite2.add(shadow.get().clone());
                size2.incrementAndGet();
            }
        }
    }

    private static void fixShadow2(Composite composite1, Composite composite2) {
        if (!(composite1 instanceof Run)) {
            return;
        }
        if (!(composite2 instanceof Run)) {
            return;
        }

        int size1 = composite1.propertiesSize();
        int size2 = composite2.propertiesSize();
        if (size1==1 && size1==size2+1) {
            final Optional<Shd> shadow = composite1.findProperty(RProp.class, Shd.class);
            final boolean isShadow = shadow.isPresent() && composite2.findProperty(RProp.class, Shd.class).isEmpty();

            if (isShadow) {
                composite2.addPropertyElement(RProp.class, (Shd)shadow.get().clone());
            }
        }
    }

    private static void fixTCellBorders_presentInRef(Composite composite1, Composite composite2, AtomicInteger size1, AtomicInteger size2) {
        if (size1.get()==size2.get() + 1) {
            final Optional<TCellBorders> tCellBorders = composite1.asStream(TCellBorders.class).findFirst().filter(o -> o.getNodes().size()==1).filter(o -> o.getNodes().get(0) instanceof Bottom);
            if (tCellBorders.isPresent() && composite2.asStream(TCellBorders.class).findFirst().isEmpty()) {
                composite2.add(tCellBorders.get().clone());
                size2.incrementAndGet();
            }
        }
    }

    private static void fixBottom_presentInResult(Composite composite1, Composite composite2, AtomicInteger size1, AtomicInteger size2) {
        if (size1.get()+1==size2.get()) {
            final Optional<Bottom> tCellBorders = composite2.asStream(Bottom.class).findFirst();
            if (tCellBorders.isPresent() && composite1.asStream(Bottom.class).findFirst().isEmpty()) {
                composite1.add(tCellBorders.get().clone());
                size1.incrementAndGet();
            }
        }
    }

    private static void fixPStyle(Composite composite1, Composite composite2, AtomicInteger size1, AtomicInteger size2) {
        if (size1.get()==size2.get() + 1) {
            final Optional<PStyle> pStyle = composite1.asStream(PStyle.class).findFirst().filter(o -> o.attributes!=null && o.attributes.size()==1)
                    .filter(o -> o.attributes.get(0).equals(Attr.get("w", "val", "1")));

            if (pStyle.isPresent() && composite2.asStream(PStyle.class).findFirst().isEmpty()) {
                composite2.add(pStyle.get().clone());
                size2.incrementAndGet();
            }
        }
    }

    @SuppressWarnings("ConstantValue")
    public static void compareProperties(CDNode node1, CDNode node2) {
        if (node1 instanceof Composite c1 && node2 instanceof Composite c2) {
            if (c1.getAlign() != c2.getAlign()) {
                Object[] args = new Object[]{c1.getClass().getSimpleName(), c1.getAlign(), c2.getClass().getSimpleName(), c2.getAlign()};
                throw new CompareException(ai.metaheuristic.commons.S.f("Nodes have different alignment, ref: %s:%s, actual: %s:%s", args));
            }
        }
        if (node1 instanceof Indentation i1 && node2 instanceof Indentation i2) {
            if (!Objects.equals(i1.getIndent(), i2.getIndent())) {
                final String s1 = i1.toString();
                final String s2 = i2.toString();
                Object[] args = new Object[]{i1.getClass().getSimpleName(), i1.getIndent(), i2.getClass().getSimpleName(), i2.getIndent()};
                throw new CompareException(ai.metaheuristic.commons.S.f("Nodes have different indent, ref: %s:%s, actual: %s:%s.\nnode1: " + s1 + "\node2: " + s2, args));
            }
        }
        if (!node1.instanceOfHasProperty() || !node2.instanceOfHasProperty()) {
            return;
        }
        List<Property> props1 = node1.asHasProperty().streamProperties()
                .sorted(Comparator.comparing(node -> node.getClass().getSimpleName())).collect(Collectors.toList());
        List<Property> props2 = node2.asHasProperty().streamProperties()
                .sorted(Comparator.comparing(node -> node.getClass().getSimpleName())).collect(Collectors.toList());

        fixTblPropEx(props1, props2);

        if (props1.size() != props2.size()) {
            throw new CompareException(ai.metaheuristic.commons.S.f("Nodes have different number of properties, ref count: %d, actual count: %d", props1.size(), props2.size()));
        }
        for (int i = 0; i < props1.size(); i++) {
            CDNode subNode1 = props1.get(i);
            CDNode subNode2 = props2.get(i);
            compare(subNode1, subNode2);
        }
    }

    private static void fixTblPropEx(List<Property> props1, List<Property> props2) {
        if (props1.size() + 1==props2.size()) {
            final Optional<TblPropEx> tblPropEx= props2.stream().filter(o->o instanceof TblPropEx).map(o->(TblPropEx)o).findFirst();
            if (tblPropEx.isPresent() && props1.stream().filter(o->o instanceof TblPropEx).findFirst().isEmpty()) {
                props1.add((Property)tblPropEx.get().clone());
                List<Property> temp = props1.stream().sorted(Comparator.comparing(node -> node.getClass().getSimpleName())).collect(Collectors.toList());
                props1.clear();
                props1.addAll(temp);
            }
        }
    }

    private static void compareFields(CDNode node1, CDNode node2) {
        if (node1.instanceOfText()) {
            compareText(node1.asText(), node2.asText());
        }
    }

    private static void compareText(Text text1, Text text2) {
        final String t1 = text1.getText();
        final String t2 = text2.getText();
        if (t1.length()!=t2.length()) {
            throw new CompareException("lengths are different. t1: " + t1.length() +", t2: " + t2.length() + System.lineSeparator() + t1 + System.lineSeparator() + t2);
        }
        if (!t1.equals(t2)) {
            throw new CompareException("texts are different:" + System.lineSeparator() + t1 + System.lineSeparator() + t2);
        }
    }

    private static void compareTypes(CDNode node1, CDNode node2) {
        if (!node1.getClass().getSimpleName().equals(node2.getClass().getSimpleName())) {
            throw new CompareException("#3 types are different. ref type: " + node1.getClass().getSimpleName() + ", actual type: " + node2.getClass().getSimpleName());
        }
    }

    private static void compareAttributes(CDNode node1, CDNode node2) {
        List<Attr> attributes1 = ((AbstractCDNode) node1).attributes;
        if (attributes1 != null) {
            List<Attr> attributes2 = ((AbstractCDNode) node2).attributes;
            if (attributes2 == null) {
                throw new CompareException("attributes are different");
            }
            if (attributes1.size()!=attributes2.size()) {
                throw new CompareException("number of attributes are different");
            }
            for (Attr attr : attributes1) {
                if (!checkAttr(attributes2, attr.nameSpace, attr.name, attr.value)) {
                    throw new CompareException("attributes are different");
                }
            }
        } else if (((AbstractCDNode) node2).attributes != null) {
            throw new CompareException("attributes are different");
        }
    }

    private static boolean checkAttr(List<Attr> attributes, @Nullable String ns, String name, String value) {
        return attributes.stream().anyMatch(attr -> attr.nameSpace != null && attr.nameSpace.equalsIgnoreCase(ns) &&
                attr.name.equalsIgnoreCase(name) &&
                attr.value.equalsIgnoreCase(value));
    }

}
