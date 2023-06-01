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
import ai.metaheuristic.ww2003.document.tags.xml.Attr;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class AbstractCDNode implements CDNode {

    @Nullable
    public List<Attr> attributes;

    @Nullable
    private Composite parent;

    @Nullable
    private CDNode prev;

    @Nullable
    private CDNode next;

    @SuppressWarnings("UnusedAssignment")
    private int nodeId=-1;

    public AbstractCDNode() {
        this.nodeId = CDNodeUtils.getNewNodeId();
        int i=0;
    }

    @Override
    public Composite getParent() {
        if (parent==null) {
            throw new NullPointerException("(parent==null)");
        }
        return parent;
    }

    public void setParent(@Nullable Composite parent) {
        this.parent = parent;
    }

    @Override
    public CDNode getPrev() {
        if (prev==null) {
            throw new NullPointerException("(prev==null)");
        }
        return prev;
    }

    public void setPrev(@Nullable CDNode prev) {
        this.prev = prev;
    }

    @Override
    public CDNode getNext() {
        if (next==null) {
            throw new NullPointerException("(next==null)");
        }
        return next;
    }

    public void setNext(@Nullable CDNode next) {
        this.next = next;
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    @Override
    public boolean hasPrev() {
        return prev != null;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public Optional<CDNode> findNextWithText() {
        CDNode node = this;
        while (node.hasNext()) {
            if ((node = node.getNext()).isNotBlank()) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    public int getNodeId() {
        if (nodeId==-1) {
            throw new DocumentProcessingException("215.020 Constructor AbstractCDNode() must be called");
        }
        return nodeId;
    }

    @Override
    public Optional<Attr> findAttributeByName(@NonNull String name) {
        if (attributes == null || attributes.isEmpty()) {
            return Optional.empty();
        }
        return attributes.stream().filter(attr -> attr.name.equals(name)).findFirst();
    }

    @Override
    public void addAttribute(@NonNull Attr attr) {
        if (attributes == null) {
            attributes = new ArrayList<>();
        }
        attributes.add(attr);
    }

    @Override
    public void replaceAttribute(Attr oldAttr, Attr newAttr) {
        if (attributes == null) {
            return;
        }
        final int index = attributes.indexOf(oldAttr);
        if (index != -1) {
            attributes.set(index, newAttr);
        }
    }

    @Override
    public void updateAttributeByName(String attrName, Attr newAttr) {
        this.findAttributeByName(attrName).ifPresentOrElse(
                attr -> this.replaceAttribute(attr, newAttr),
                () -> this.addAttribute(newAttr));
    }

    @Override
    public boolean removeAttribute(Attr attr) {
        if (attributes == null) {
            return true;
        }
        return attributes.remove(attr);
    }

    @SuppressWarnings("unchecked")
    protected <T extends CDNode> Enums.ContinueStrategy getNodeList(NodeFilters.FilteringContext context, Class<? extends CDNode> clazz, NodeFilters.Filter ... filters) {
        if (clazz.isAssignableFrom(this.getClass())) {
            for (NodeFilters.Filter filter : filters) {
                final NodeFilters.FilterResult apply = filter.getFilter().apply(this);
                if (apply.accepted()) {
                    context.consumer.accept((T) this);
//                    context.list.add((T) this);
                    if (apply.strategy()== Enums.ContinueStrategy.stop) {
                        return apply.strategy();
                    }
                }
            }
        }
        return Enums.ContinueStrategy.non_stop;
    }

    @SneakyThrows
    @Override
    public AbstractCDNode clone() {
        AbstractCDNode clone = (AbstractCDNode) super.clone();
        if (attributes != null) {
            clone.attributes = new ArrayList<>(attributes);
        }
        clone.prev = null;
        clone.next = null;
        clone.parent = null;
        return clone;
    }

    @Override
    public void destroy() {
        if (attributes != null) {
            attributes.clear();
            attributes = null;
        }
        parent = null;
        next = null;
        prev = null;
    }

}
