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

import ai.metaheuristic.ww2003.document.tags.xml.Attr;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static ai.metaheuristic.ww2003.document.Enums.Relation.*;

public abstract class Leaf extends AbstractCDNode {

    public Leaf(List<Attr> attrs) {
        super();
        if (attrs.isEmpty()) {
            attributes = null;
        } else {
            attributes = new ArrayList<>(attrs);
        }
    }

    public Leaf(Attr... attrs) {
        super();
        if (attrs.length == 0) {
            attributes = null;
        } else {
            attributes = new ArrayList<>(attrs.length+2);
            Collections.addAll(attributes, attrs);
        }
    }

    public Leaf() {
        super();
    }

    @Override
    public <T extends CDNode> Stream<T> asStream(Class<? extends T> clazz, NodeFilters.Filter ... filters) {
        return asStream(clazz, DESCENDANT, filters);
    }

    @Override
    public <T extends CDNode> Stream<T> asStream(Class<? extends T> clazz, Enums.Relation relation, NodeFilters.Filter ... filters) {
        boolean accepted = false;
        for (NodeFilters.Filter filter : filters) {
            if (!filter.getFilter().apply(this).accepted()) {
                accepted = true;
                break;
            }
        }
        if (!accepted) {
            return Stream.empty();
        }
        if (clazz.isAssignableFrom(this.getClass())) {
            return (Stream<T>) Stream.of(this);
        }
        return Stream.empty();
    }

    @Override
    public <T extends CDNode> Stream<T> asStream(Class<? extends T> clazz) {
        return asStream(clazz, NodeFilters.POSITIVE_FILTER);
    }

    @Nullable
    public abstract String tag();

    @Override
    public Leaf clone() {
        return (Leaf) super.clone();
    }

}
