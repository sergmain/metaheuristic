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

package ai.metaheuristic.ww2003.document;

import ai.metaheuristic.ww2003.Enums;
import ai.metaheuristic.ww2003.document.exceptions.DocumentProcessingException;
import ai.metaheuristic.ww2003.document.persistence.ww2003.property.WW2003PropertyUtils;
import ai.metaheuristic.ww2003.document.tags.HasProperty;
import ai.metaheuristic.ww2003.document.tags.Indentation;
import ai.metaheuristic.ww2003.document.tags.Property;
import ai.metaheuristic.ww2003.document.tags.PropertyElement;
import ai.metaheuristic.ww2003.document.tags.ww2003.DummyNode;
import ai.metaheuristic.ww2003.document.tags.xml.*;
import ai.metaheuristic.ww2003.document.tags.xml.table.TCellBorders;
import ai.metaheuristic.ww2003.utils.ThreadUtils;
import lombok.Getter;
import javax.annotation.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"rawtypes"})
public abstract class Composite extends AbstractCDNode implements HasProperty, Processing{

    private Enums.Align align = Enums.Align.none;

    @Nullable
    @Getter
    private LinkedList<Property> properties;

    private List<CDNode> nodes = new ArrayList<>();

    public List<CDNode> getNodes() {
        return nodes;
    }

    public Composite(CDNode... nodes) {
        super();
        for (CDNode node : nodes) {
            if (node.instanceOfProperty()) {
                setProperty(node.asProperty());
            } else {
                add(node);
            }
        }
    }

    public Stream<CDNode> streamNodes() {
        // dirty trick to be able to delete node from node list not breaking stream
        List<CDNode> list = new ArrayList<>(nodes);
        return list.stream();
    }

    public List<CDNode> getNodeListCopy() {
        return new ArrayList<>(nodes);
    }

    /**
     * returns true if node has at least one first-level child with given type
     */
    public boolean hasChild(Class clazz) {
        for (CDNode node : nodes) {
            if (clazz.isInstance(node)) {
                return true;
            }
        }
        return false;
    }

    public void initProperties() {
        if (properties == null) {
            properties = new LinkedList<>();
        }
    }

    public void add(CDNode node) {
        if (this instanceof TCellBorders) {
            int i=0;
        }
        ThreadUtils.checkInterrupted();
        AbstractCDNode aNode = (AbstractCDNode) node;
        if (aNode instanceof Jc jc) {
            if (!this.hasParent()) {
                throw new DocumentProcessingException("225.010 Jc can't be added to PProp without parent");
            }
            Composite parent = this.getParent();
            if (!parent.instanceOfStyle() && !(parent instanceof DummyNode)) {
                parent.setAlign(WW2003PropertyUtils.getAlign(jc));
                return;
            }
        }

        if (aNode instanceof Ind ind) {
            if (ind.attributes!=null && ind.attributes.stream().anyMatch(a->"first-line".equals(a.name))) {
                if (!this.hasParent()) {
                    throw new DocumentProcessingException("225.030 Ind can't be added to PProp without parent");
                }
                Composite parent = this.getParent();
                if (!parent.instanceOfStyle() && !(parent instanceof DummyNode) && parent instanceof Indentation indentation) {
                    indentation.setIndent(WW2003PropertyUtils.getIndent(ind));
                    return;
                }
            }
        }

        aNode.setParent(this);
        if (!nodes.isEmpty()) {
            AbstractCDNode lastNode = (AbstractCDNode) nodes.get(nodes.size() - 1);
            aNode.setPrev(lastNode);
            lastNode.setNext(node);
        } else {
            aNode.setPrev(null);
        }
        aNode.setNext(null);
        nodes.add(aNode);
    }

    public void add(List<? extends CDNode> nodes) {
        nodes.forEach(this::add);
    }

    public void remove(CDNode node) {
        ThreadUtils.checkInterrupted();
        AbstractCDNode aNode = (AbstractCDNode) node;
        AbstractCDNode nodePrev = aNode.hasPrev() ? (AbstractCDNode) aNode.getPrev() : null;
        AbstractCDNode nodeNext = aNode.hasNext() ? (AbstractCDNode) aNode.getNext() : null;
        if (nodePrev != null) {
            nodePrev.setNext(nodeNext);
        }
        if (nodeNext != null) {
            nodeNext.setPrev(nodePrev);
        }
        aNode.setParent(null);
        aNode.setPrev(null);
        aNode.setNext(null);
        nodes.remove(aNode);
    }

    public void removeAll() {
        nodes.clear();
    }

    public int replace(CDNode target, CDNode replacement) {
        int index = nodes.indexOf(target);
        if (index != -1) {
            target.removeFromParent();
            if (index == nodes.size()) {
                add(replacement);
            } else {
                insert(index, replacement);
            }
        }
        return index;
    }

    public int replace(CDNode target, List<? extends CDNode> replacementNodes) {
        int index = nodes.indexOf(target);
        if (index != -1) {
            set(index, replacementNodes);
        }
        return index;
    }

    public int replace(List<? extends CDNode> targetNodes, List<? extends CDNode> replacementNodes) {
        if (targetNodes.isEmpty()) {
            throw new DocumentProcessingException("225.015 targetNodes empty");
        }
        for (CDNode targetNode : targetNodes) {
            if (!this.contains(targetNode)) {
                throw new DocumentProcessingException("225.020 node wasn't fount. node: " +targetNode.getText()+
                        ", targetNode.class: " + targetNode.getClass().getSimpleName()+", nodes (max 10 nodes): " + nodes.stream().limit(10).map(o->o.getClass().getSimpleName()).collect(Collectors.joining(", ")) );
            }
        }
        List<CDNode> targetNodesCopy = new ArrayList<>(targetNodes);
        CDNode firstTarget = targetNodesCopy.remove(0);
        int index = nodes.indexOf(firstTarget);
        if (index != -1) {
            set(index, replacementNodes);
            targetNodesCopy.forEach(this::remove);
        }
        return index;
    }

    public void set(int index, List<? extends CDNode> nodes) {
        List<CDNode> replacementNodesCopy = new ArrayList<>(nodes);
        CDNode firstNode = replacementNodesCopy.remove(0);
        set(index, firstNode);
        for (int i = replacementNodesCopy.size() - 1; i >= 0; i--) {
            insertAfter(firstNode, replacementNodesCopy.get(i));
        }
    }

    public void set(int index, CDNode node) {
        AbstractCDNode aNode = (AbstractCDNode) node;
        AbstractCDNode currentNode = (AbstractCDNode) nodes.get(index);
        aNode.setParent(this);
        AbstractCDNode prev = null;
        if (currentNode.hasPrev()) {
            prev = (AbstractCDNode) currentNode.getPrev();
            prev.setNext(aNode);
        }
        aNode.setPrev(prev);

        AbstractCDNode next = null;
        if (currentNode.hasNext()) {
            next = (AbstractCDNode) currentNode.getNext();
            next.setPrev(aNode);
        }
        aNode.setNext(next);

        nodes.set(index, aNode);
    }

    public void insert(int index, CDNode node) {
        ThreadUtils.checkInterrupted();
        AbstractCDNode aNode = (AbstractCDNode) node;
        if (nodes.size() > 0) {
            // will be not the last element
            if (nodes.size()>index) {
                AbstractCDNode current = (AbstractCDNode) nodes.get(index);
                aNode.setNext(current);
                aNode.setPrev(current.hasPrev() ? current.getPrev() : null);
                current.setPrev(aNode);
            }
            else if (nodes.size()==index) {
                // insert as the last element
                aNode.setPrev(nodes.get(index-1));
            }
        }
        if (aNode.hasPrev()) {
            AbstractCDNode prev = (AbstractCDNode) aNode.getPrev();
            prev.setNext(aNode);
        }
        aNode.setParent(this);
        nodes.add(index, node);
    }

    public void insertBefore(CDNode before, CDNode node) {
        if (!before.hasParent() || before.getParent() != this) {
            throw new DocumentProcessingException("225.040 Node not found");
        }
        int index = nodes.indexOf(before);
        insert(index, node);
    }

    public void insertAfter(CDNode after, CDNode node) {
        if (!after.hasParent() || after.getParent() != this) {
            throw new DocumentProcessingException("225.060 Node not found");
        }
        int index = nodes.indexOf(after);
        if (index == nodes.size() - 1) {
            add(node);
        } else {
            insert(index + 1, node);
        }
    }

    public void insertAfter(CDNode after, List<CDNode> nodes) {
        if (!after.hasParent() || after.getParent() != this) {
            throw new DocumentProcessingException("225.080 Node not found");
        }
        for (CDNode node : nodes) {
            insertAfter(after, node);
            after = node;
        }
    }

    public int indexOf(CDNode node) {
        return nodes.indexOf(node);
    }

    public boolean contains(CDNode node) {
        return nodes.contains(node);
    }

    public CDNode get(int index) {
        return nodes.get(index);
    }

    public <T extends CDNode> Optional<T> findFirst(Class<T> clazz) {
        NodeFilters.FilteringContext context = new NodeFilters.FilteringContext();
        getNodeList(context, clazz, NodeFilters.FIND_FIRST_FILTER);
        if (context.list.size()>1) {
            throw new DocumentProcessingException("225.140 (list.size()>1)");
        }
        return context.list.isEmpty() ? Optional.empty() : Optional.ofNullable((T)context.list.get(0));
    }

    public <T extends CDNode> Optional<T> findLast(Class<T> clazz) {
        if (clazz==CDNode.class || clazz==Composite.class) {

            throw new DocumentProcessingException("225.160 CDNode.class and Composite.class not supported. Use p.asStream(CDNode.class, Enums.Relation.ALL_DESCENDANTS).reduce((n1, n2) -> n2)");
        }
        NodeFilters.FilteringContext context = new NodeFilters.FilteringContext();
        getNodeList(context, clazz, NodeFilters.POSITIVE_FILTER);
        return context.list.isEmpty() ? Optional.empty() : Optional.ofNullable((T)context.list.getLast());
    }

    @Nullable
    public <T extends CDNode> T getLast(Class<T> clazz) {
        return asStream(clazz).reduce((first, second) -> second).orElse(null);
    }

    public int size() {
        return nodes.size();
    }

    @Override
    public <T extends CDNode> Stream<T> asStream(Class<? extends T> clazz, NodeFilters.Filter ... filters) {
        return asStream(clazz, ai.metaheuristic.ww2003.document.Enums.Relation.DESCENDANT, filters);
    }

    @Override
    public <T extends CDNode> Stream<T> asStream(Class<? extends T> clazz, ai.metaheuristic.ww2003.document.Enums.Relation relation, NodeFilters.Filter ... filters) {
        NodeFilters.FilteringContext context = new NodeFilters.FilteringContext(relation);
        final NodeFilters.Filter[] actualFilters = filters.length == 0 ? NodeFilters.POSITIVE_FILTERS : filters;
        if (relation==ai.metaheuristic.ww2003.document.Enums.Relation.CHILD) {
            getNodeListFromChild(context, clazz, actualFilters);
        }
        else {
            getNodeList(context, clazz, actualFilters);
        }
        return (Stream<T>) ((List<? extends T>) (List<T>) context.list).stream();
    }

    @Override
    public <T extends CDNode> void process(Class<? extends T> clazz, ai.metaheuristic.ww2003.document.Enums.Relation relation, NodeFilters.Filter filter, Consumer<T> consumer) {
        NodeFilters.FilteringContext<T> context = new NodeFilters.FilteringContext<>(relation, consumer);
        final NodeFilters.Filter[] actualFilters = new NodeFilters.Filter[]{filter};
        if (relation==ai.metaheuristic.ww2003.document.Enums.Relation.CHILD) {
            getNodeListFromChild(context, clazz, actualFilters);
        }
        else {
            getNodeList(context, clazz, actualFilters);
        }
    }

    private <T extends CDNode> void getNodeListFromChild(NodeFilters.FilteringContext context, Class<? extends T> clazz, NodeFilters.Filter[] filters) {
        for (CDNode node : nodes) {
            if (clazz.isAssignableFrom(node.getClass())) {
                for (NodeFilters.Filter filter : filters) {
                    final NodeFilters.FilterResult apply = filter.getFilter().apply(node);
                    if (apply.accepted()) {
//                        context.list.add((T) node);
                        context.consumer.accept((T) node);
                        if (apply.strategy() == Enums.ContinueStrategy.stop) {
                            return;
                        }
                    }
                }
            }
        }
    }

    protected <T extends CDNode> Enums.ContinueStrategy getNodeList(NodeFilters.FilteringContext context, Class<? extends CDNode> clazz, NodeFilters.Filter ... filters) {
        if (clazz.isAssignableFrom(this.getClass())) {
            for (NodeFilters.Filter filter : filters) {
                final NodeFilters.FilterResult apply = filter.getFilter().apply(this);
                if (apply.accepted()) {
                    context.consumer.accept((T) this);

                    if (apply.strategy()== Enums.ContinueStrategy.stop) {
                        return Enums.ContinueStrategy.stop;
                    }
                    if (context.relation!=ai.metaheuristic.ww2003.document.Enums.Relation.ALL_DESCENDANTS && clazz!=Composite.class && clazz!=HasProperty.class) {
                        return Enums.ContinueStrategy.non_stop;
                    }
                }
                if (apply.strategy()== Enums.ContinueStrategy.stop) {
                    return Enums.ContinueStrategy.stop;
                }
            }
        }
        for (CDNode node : nodes) {
            if (clazz.isAssignableFrom(node.getClass())) {

                for (NodeFilters.Filter filter : filters) {
                    final NodeFilters.FilterResult apply = filter.getFilter().apply(this);
                    if (apply.accepted() && apply.strategy()== Enums.ContinueStrategy.stop) {
                        context.consumer.accept((T) node);

                        return Enums.ContinueStrategy.stop;
                    }
                    if (context.relation==ai.metaheuristic.ww2003.document.Enums.Relation.DESCENDANT || context.relation==ai.metaheuristic.ww2003.document.Enums.Relation.ALL_DESCENDANTS) {
                        if (((AbstractCDNode) node).getNodeList(context, clazz, filters) == Enums.ContinueStrategy.stop) {
                            return Enums.ContinueStrategy.stop;
                        }
                    }
                }
            } else {
                if (context.relation==ai.metaheuristic.ww2003.document.Enums.Relation.DESCENDANT || context.relation==ai.metaheuristic.ww2003.document.Enums.Relation.ALL_DESCENDANTS) {
                    if (((AbstractCDNode) node).getNodeList(context, clazz, filters) == Enums.ContinueStrategy.stop) {
                        return Enums.ContinueStrategy.stop;
                    }
                }
            }
        }
        return Enums.ContinueStrategy.non_stop;
    }

    @Override
    public <T extends CDNode> Stream<T> asStream(Class<? extends T> clazz) {
        return asStream(clazz, NodeFilters.POSITIVE_FILTER);
    }

    @Override
    public Composite clone() {
        Composite clone = (Composite) super.clone();
        clone.nodes = new ArrayList<>();
        nodes.stream().map(CDNode::clone).forEach(clone::add);
        if (properties != null) {
            clone.properties = new LinkedList<>();
            properties.stream().map(CDNode::clone).map(CDNode::asProperty).forEach(clone::setProperty);
        }
        clone.setAlign(this.getAlign());
        return clone;
    }

    @Override
    public void destroy() {
        super.destroy();
        for (CDNode node : nodes) {
            if (node!=null) {
                node.destroy();
            }
        }
        final List<Property> propertiesTemp = this.properties;
        this.properties = null;

        if (propertiesTemp != null) {
            while(!propertiesTemp.isEmpty()) {
                try {
                    Property property = propertiesTemp.get(0);
                    if (property != null) {
                        property.destroy();
                    }
                }
                catch(NullPointerException | NoSuchElementException | IndexOutOfBoundsException e) {
                    // do nothing
                }

                try {
                    propertiesTemp.remove(0);
                }
                catch(NullPointerException | NoSuchElementException | IndexOutOfBoundsException e) {
                    // do nothing
                }
            }
        }
    }

    public void unAssign() {
        setParent(null);
        setNext(null);
        setPrev(null);
    }

    public void assign(Composite parent) {
        setParent(parent);
    }

    @Override
    public Stream<Property> streamProperties() {
        if (properties == null) {
            return Stream.of();
        }
        return properties.stream();
    }

    @Override
    public int propertiesSize() {
        if (properties == null) {
            return 0;
        }
        return properties.size();
    }

    @Override
    public void setProperty(Property property) {
        setPropertyStatic(this, property, true);
    }

    @Override
    public void setProperty(Property property, boolean process) {
        setPropertyStatic(this, property, process);
    }

    public static void setPropertyStatic(Composite composite, Property property, boolean process) {
        property.asComposite().unAssign();
        property.asComposite().assign(composite);
        if (process) {
            if (property instanceof PProp pProp) {
                composite.processPProp(pProp);
            }
        }

        if (composite.properties == null) {
            composite.properties = new LinkedList<>();
        }
        composite.removeProperty(property.getClass());
        composite.properties.add(property);
    }

    private void processPProp(PProp pProp) {
        final Jc jc = pProp.findFirst(Jc.class).filter(o -> o.attributes != null).orElse(null);
        Enums.Align align = processJc(jc);
        if (align!= Enums.Align.none) {
            pProp.getNodes().remove(jc);
        }
        final Ind ing = pProp.findFirst(Ind.class).filter(o -> o.attributes != null).orElse(null);
        Integer ident = processInd(ing);
        if (ident!=null) {
            pProp.getNodes().remove(ing);
        }
    }

    private Enums.Align processJc(@Nullable Jc jc) {
        final Enums.Align align = WW2003PropertyUtils.getAlign(jc);
        if (align!= Enums.Align.none) {
            this.align = align;
        }
        return align;
    }

    @Nullable
    private Integer processInd(@Nullable Ind ind) {
        if (ind !=null && ind.attributes!=null) {
            String indentStr = ind.attributes.stream().filter(a -> "first-line".equals(a.name) && "w".equals(a.nameSpace)).findFirst().map(attr -> attr.value).stream().findFirst().orElse(null);
            if (indentStr==null) {
                return null;
            }
            if (this instanceof Indentation indentation) {
                final Integer indent = Integer.valueOf(indentStr);
                indentation.setIndent(indent);
                return indent;
            }
        }
        return null;
    }

    @Override
    public void removeProperty(Class<? extends Property> clazz) {
        if (properties == null) {
            return;
        }
        List<Property> propsToRemove = properties.stream()
                .filter(prop -> clazz.isAssignableFrom(prop.getClass())).toList();
        properties.removeAll(propsToRemove);
        propsToRemove.forEach(property -> property.asComposite().unAssign());
    }

    @Override
    public void addPropertyElement(Class<? extends Property> propertyClass, PropertyElement propertyElement) {
        addPropertyElement(propertyClass, propertyElement, true);
    }

    public void addPropertyElement(Class<? extends Property> propertyClass, PropertyElement propertyElement, boolean process) {
        if (process) {
            if (propertyElement instanceof Jc jc) {
                Enums.Align align = processJc(jc);
                if (align != Enums.Align.none) {
                    return;
                }
            }
        }

        if (properties == null) {
            properties = new LinkedList<>();
        }
        final Optional<? extends PropertyElement> element = findProperty(propertyClass, propertyElement.getClass());
        if (propertyElement instanceof CustomProperty customProperty && element.isPresent()) {
            PProp prop = (PProp) findProperty(propertyClass).orElseThrow();
            prop.remove(element.get());
            prop.add(customProperty);
            return;
        }
        element.ifPresentOrElse(
                prEl -> prEl.getParent().replace(prEl, propertyElement),
                () -> findProperty(propertyClass).ifPresentOrElse(
                        pr -> {
                            if (propertyElement instanceof Composite c) {
                                c.assign(pr.asComposite());
                            }
                            pr.asComposite().add(propertyElement);
                        },
                        () -> {
                            if (propertyClass.isAssignableFrom(PProp.class)) {
                                final PProp pProp = new PProp();
                                if (propertyElement instanceof Composite c) {
                                    c.assign(pProp);
                                }
                                pProp.assign(this);
                                pProp.add(propertyElement);
                                setProperty(pProp, false);
                            } else if (propertyClass.isAssignableFrom(RProp.class)) {
                                setProperty(new RProp(propertyElement));
                            }
                        }
                )
        );
    }

    public Enums.Align getAlign() {
        return align;
    }

    public void setAlign(Enums.Align align) {
        this.align = align;
    }
}
