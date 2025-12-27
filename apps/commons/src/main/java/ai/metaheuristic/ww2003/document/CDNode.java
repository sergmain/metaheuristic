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

package ai.metaheuristic.ww2003.document;

import ai.metaheuristic.ww2003.document.tags.HasProperty;
import ai.metaheuristic.ww2003.document.tags.Property;
import ai.metaheuristic.ww2003.document.tags.PropertyElement;
import ai.metaheuristic.ww2003.document.tags.xml.*;
import ai.metaheuristic.ww2003.document.tags.xml.table.*;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface CDNode extends Streamable, Writable, Cloneable, Finalizable {

    Composite getParent();

    CDNode getPrev();

    CDNode getNext();

    @SuppressWarnings("unchecked")
    default <T extends CDNode> Optional<T> findNext(Class<T> clazz) {
        CDNode next = this;
        while (next.hasNext()) {
            next = next.getNext();
            if (clazz.isAssignableFrom(next.getClass())) {
                return Optional.of((T) next);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    default <T extends CDNode> Optional<T> findNextExactly(Class<T> clazz) {
        CDNode next = this;
        while (next.hasNext()) {
            next = next.getNext();
            if (clazz.equals(next.getClass())) {
                return Optional.of((T) next);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    default <T extends CDNode> Optional<T> findPrev(Class<T> clazz) {
        CDNode prev = this;
        while (prev.hasPrev()) {
            prev = prev.getPrev();
            if (clazz.isAssignableFrom(prev.getClass())) {
                return Optional.of((T) prev);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    default <T extends CDNode> Optional<T> findPrevExactly(Class<T> clazz) {
        CDNode prev = this;
        while (prev.hasPrev()) {
            prev = prev.getPrev();
            if (clazz.equals(prev.getClass())) {
                return Optional.of((T) prev);
            }
        }
        return Optional.empty();
    }

    Optional<CDNode> findNextWithText();

    Optional<Attr> findAttributeByName(String name);

    void addAttribute(Attr attr);

    void replaceAttribute(Attr oldAttr, Attr newAttr);

    void updateAttributeByName(String attrName, Attr newAttr);

    boolean removeAttribute(Attr attr);

    default <T extends CDNode> Optional<T> findFirst(Class<T> clazz) {
        return Optional.empty();
    }

    default <T extends CDNode> Optional<T> findLast(Class<T> clazz) {
        return Optional.empty();
    }

    boolean hasPrev();

    boolean hasNext();

    boolean hasParent();

    default String getTextSmart() {
        return CDNodeUtils.getTextSmart(this);
    }

    default String getText() {
        return asStream(Text.class).map(Text::getText).collect(Collectors.joining());
    }

    default String getTextWithSpaceDelimiter() {
        return asStream(Text.class).map(Text::getText).collect(Collectors.joining(" "));
    }

    default boolean containsText(String str) {
        return asStream(Text.class, new NodeFilters.TextContainsFilter(str)).findFirst().isPresent();
    }

    default boolean containsTextEquals(String str) {
        return asStream(Text.class, new NodeFilters.TextEqualsFilter(str)).findFirst().isPresent();
    }

    default boolean containsTextStarts(String str) {
        return asStream(Text.class, new NodeFilters.TextStartsFilter(str)).findFirst().isPresent();
    }

    default boolean containsTextEnds(String str) {
        return asStream(Text.class, new NodeFilters.TextEndsFilter(str)).findFirst().isPresent();
    }

    default Composite asComposite() {
        return (Composite) this;
    }

    default Body asBody() {
        return (Body) this;
    }

    default Sect asSect() {
        return (Sect) this;
    }

    default Para asPara() {
        return (Para) this;
    }

    default Run asRun() {
        return (Run) this;
    }

    default Pict asPict() {
        return (Pict) this;
    }

    default RProp asRProp() {
        return (RProp) this;
    }

    default Property asProperty() {
        return (Property) this;
    }

    default PropertyElement asPropertyElement() {
        return (PropertyElement) this;
    }

    default HasProperty asHasProperty() {
        return (HasProperty) this;
    }

    default PProp asPProp() {
        return (PProp) this;
    }

    default Text asText() {
        return (Text) this;
    }

    default TextContainer asTextContainer() {
        return (TextContainer) this;
    }

    default Tbl asTbl() {
        return (Tbl) this;
    }

    default TRow asTRow() {
        return (TRow) this;
    }

    default TCell asTCell() {
        return (TCell) this;
    }

    default BinData asBinData() {
        return (BinData) this;
    }

    default UnIdentifiedNode asUnIdentifiedNode() {
        return (UnIdentifiedNode) this;
    }

    default XmlTag asXmlTag() {
        return (XmlTag) this;
    }

    default Styles asStyles() {
        return (Styles) this;
    }

    default boolean instanceOfComposite() {
        return this instanceof Composite;
    }

    default boolean instanceOfPara() {
        return this instanceof Para;
    }

    default boolean instanceOfProperty() {
        return this instanceof Property;
    }

    default boolean instanceOfHasProperty() {
        return this instanceof HasProperty;
    }

    default boolean instanceOfRProp() {
        return this instanceof RProp;
    }

    default boolean instanceOfPProp() {
        return this instanceof PProp;
    }

    default boolean instanceOfRun() {
        return this instanceof Run;
    }

    default boolean instanceOfText() {
        return this instanceof Text;
    }

    default boolean instanceOfTextContainer() {
        return this instanceof TextContainer;
    }

    default boolean instanceOfWW2003Document() {
        return this instanceof WW2003Document;
    }

    default boolean instanceOfTbl() {
        return this instanceof Tbl;
    }

    default boolean instanceOfTblGrid() {
        return this instanceof TblGrid;
    }

    default boolean instanceOfTblInd() {
        return this instanceof TblInd;
    }

    default boolean instanceOfTblLayout() {
        return this instanceof TblLayout;
    }

    default boolean instanceOfTblProp() {
        return this instanceof TblProp;
    }

    default boolean instanceOfTblPropEx() {
        return this instanceof TblPropEx;
    }

    default boolean instanceOfTblBorders() {
        return this instanceof TblBorders;
    }

    default boolean instanceOfTblCellMar() {
        return this instanceof TblCellMar;
    }

    default boolean instanceOfTRow() {
        return this instanceof TRow;
    }

    default boolean instanceOfTRowHeight() {
        return this instanceof TRowHeight;
    }

    default boolean instanceOfTop() {
        return this instanceof Top;
    }

    default boolean instanceOfTRowProp() {
        return this instanceof TRowProp;
    }

    default boolean instanceOfTCell() {
        return this instanceof TCell;
    }

    default boolean instanceOfTCellProp() {
        return this instanceof TCellProp;
    }

    default boolean instanceOfVAlign() {
        return this instanceof VAlign;
    }

    default boolean instanceOfVMerge() {
        return this instanceof VMerge;
    }

    default boolean instanceOfTCellWidth() {
        return this instanceof TCellWidth;
    }

    default boolean instanceOfTCellBorders() {
        return this instanceof TCellBorders;
    }

    default boolean instanceOfTCellMar() {
        return this instanceof TCellMar;
    }

    default boolean instanceOfPict() {
        return this instanceof Pict;
    }

    default boolean instanceOfShape() {
        return this instanceof Shape;
    }

    default boolean instanceOfBinData() {
        return this instanceof BinData;
    }

    default boolean instanceOfOLEObject() {
        return this instanceof OLEObject;
    }

    default boolean instanceOfSubSection() {
        return this instanceof SubSection;
    }

    default boolean instanceOfU() {
        return this instanceof U;
    }

    default boolean instanceOfVanish() {
        return this instanceof Vanish;
    }

    default boolean instanceOfVertAlign() {
        return this instanceof VertAlign;
    }

    default boolean instanceOfWFont() {
        return this instanceof WFont;
    }

    default boolean instanceOfWxFont() {
        return this instanceof WxFont;
    }

    default boolean instanceOfInd() {
        return this instanceof Ind;
    }

    default boolean instanceOfUnIdentifiedNode() {
        return this instanceof UnIdentifiedNode;
    }

    default boolean instanceOfPStyle() {
        return this instanceof PStyle;
    }

    default boolean instanceOfDocumentProperties() {
        return this instanceof DocumentProperties;
    }

    default boolean instanceOfXmlTag() {
        return this instanceof XmlTag;
    }

    default boolean instanceOfBody() {
        return this instanceof Body;
    }

    default boolean instanceOfBold() {
        return this instanceof Bold;
    }

    default boolean instanceOfBr() {
        return this instanceof Br;
    }

    default boolean instanceOfNoBreakHyphen() {
        return this instanceof NoBreakHyphen;
    }

    default boolean instanceOfTab() {
        return this instanceof Tab;
    }

    default boolean instanceOfBottom() {
        return this instanceof Bottom;
    }

    default boolean instanceOfGridCol() {
        return this instanceof GridCol;
    }

    default boolean instanceOfGridSpan() {
        return this instanceof GridSpan;
    }

    default boolean instanceOfInsideH() {
        return this instanceof InsideH;
    }

    default boolean instanceOfInsideV() {
        return this instanceof InsideV;
    }

    default boolean instanceOfLeft() {
        return this instanceof Left;
    }

    default boolean instanceOfRight() {
        return this instanceof Right;
    }

    default boolean instanceOfColor() {
        return this instanceof Color;
    }

    default boolean instanceOfCustomProperty() {
        return this instanceof CustomProperty;
    }

    default boolean instanceOfDocPr() {
        return this instanceof DocPr;
    }

    default boolean instanceOfFonts() {
        return this instanceof Fonts;
    }

    default boolean instanceOfHighlight() {
        return this instanceof Highlight;
    }

    default boolean instanceOfImageData() {
        return this instanceof ImageData;
    }

    default boolean instanceOfItalic() {
        return this instanceof Italic;
    }

    default boolean instanceOfJc() {
        return this instanceof Jc;
    }

    default boolean instanceOfPosition() {
        return this instanceof Position;
    }

    default boolean instanceOfSect() {
        return this instanceof Sect;
    }

    default boolean instanceOfSectProp() {
        return this instanceof SectProp;
    }

    default boolean instanceOfShd() {
        return this instanceof Shd;
    }

    default boolean instanceOfStyle() {
        return this instanceof Style;
    }

    default boolean instanceOfStyles() {
        return this instanceof Styles;
    }

    CDNode clone();

    int getNodeId();

    @SuppressWarnings("unchecked")
    default <T> Optional<T> findOuterNode(Class<T> clazz) {
        if (!hasParent()) {
            return Optional.empty();
        }
        Composite parent = getParent();
        do {
            if (clazz.isInstance(parent)) {
                break;
            }
            parent = parent.hasParent() ? parent.getParent() : null;
        }
        while (parent!=null);

        return Optional.ofNullable((T) parent);
    }

    default Optional<Composite> findOuterNodeWithParent(Class<? extends CDNode> clazz) {
        CDNode rootNode = this;
        while (rootNode.hasParent() && !clazz.isInstance(rootNode.getParent())) {
            rootNode = rootNode.getParent();
        }
        if (!rootNode.hasParent() || !clazz.isInstance(rootNode.getParent())) {
            return Optional.empty();
        }
        return Optional.of(rootNode.asComposite());
    }

    default <T extends CDNode> Optional<T> findSibling(Class<T> clazz) {
        return findSibling(clazz, Enums.BypassDirection.FORWARD);
    }

    default <T extends CDNode> Optional<T> findSibling(Class<T> clazz, int limit) {
        return findSibling(clazz, Enums.BypassDirection.FORWARD, limit);
    }

    @SuppressWarnings("unchecked")
    default <T extends CDNode> Optional<T> findSibling(Class<T> clazz, Enums.BypassDirection direction) {
        return findSibling(clazz, direction, 1_000_000);
    }

    @SuppressWarnings("unchecked")
    default <T extends CDNode> Optional<T> findSibling(Class<T> clazz, Enums.BypassDirection direction, int limit) {
        CDNode node;
        if (direction==Enums.BypassDirection.FORWARD) {
            if (!this.hasNext()) {
                return Optional.empty();
            }
            node = this.getNext();}
        else {
            if (!this.hasPrev()) {
                return Optional.empty();
            }
            node = this.getPrev();
        }
        int counter = 1;
        while (node != null && !clazz.isInstance(node)) {
            if (counter == limit) {
                return Optional.empty();
            }
            if (direction==Enums.BypassDirection.FORWARD) {
                node = node.hasNext() ? node.getNext() : null;
            }
            else {
                node = node.hasPrev() ? node.getPrev() : null;
            }
            counter++;
        }
        return Optional.ofNullable((T) node);
    }

    @SuppressWarnings("unchecked")
    default <T extends CDNode> List<T> findSiblings(Class<T> clazz, Enums.BypassDirection direction) {
        return findSiblings(clazz, direction, false, null);
    }

    default <T extends CDNode> List<T> findSiblings(Class<T> clazz, Enums.BypassDirection direction, boolean includeThis) {
        return findSiblings(clazz, direction, includeThis, null);
    }

    default <T extends CDNode> List<T> findSiblings(Class<T> clazz, Enums.BypassDirection direction, boolean includeThis, NodeFilters.@Nullable Filter filter) {
        final NodeFilters.Filter actualFilter = filter == null ? new NodeFilters.InstanceFilter(clazz)  : filter;
        CDNode node = this;
        List<T> nodes = new ArrayList<>();
        if (includeThis) {
            final NodeFilters.FilterResult apply = actualFilter.getFilter().apply(node);
            if (apply.accepted()) {
                nodes.add((T) node);
                if (apply.strategy() == ai.metaheuristic.ww2003.Enums.ContinueStrategy.stop) {
                    return nodes;
                }
            }
        }
        if (direction==Enums.BypassDirection.FORWARD) {
            while (node.hasNext()) {
                node = node.getNext();
                final NodeFilters.FilterResult apply = actualFilter.getFilter().apply(node);
                if (apply.accepted()) {
                    nodes.add((T) node);
                    if (apply.strategy() == ai.metaheuristic.ww2003.Enums.ContinueStrategy.stop) {
                        return nodes;
                    }
                }
            }
        } else {
            while (node.hasPrev()) {
                node = node.getPrev();
                final NodeFilters.FilterResult apply = actualFilter.getFilter().apply(node);
                if (apply.accepted()) {
                    nodes.add((T) node);
                    if (apply.strategy() == ai.metaheuristic.ww2003.Enums.ContinueStrategy.stop) {
                        return nodes;
                    }
                }
            }
        }
        return nodes;
    }

    default void removeFromParent() {
        if (hasParent()) {
            getParent().remove(this);
        }
    }

    default boolean isBlank() {
        if (instanceOfText()) {
            return StringUtils.isBlank(asText().getText());
        } else if (instanceOfPict()) {
            return false;
        } else if (instanceOfComposite()) {
            return asComposite().getNodes().stream().allMatch(CDNode::isBlank);
        }
        return true;
    }

    default boolean isNotBlank() {
        return !isBlank();
    }
}
