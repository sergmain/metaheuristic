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

package ai.metaheuristic.ww2003.document.tags.xml;

import ai.metaheuristic.ww2003.Enums;
import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.Composite;
import ai.metaheuristic.ww2003.document.tags.HasProperty;
import ai.metaheuristic.ww2003.document.tags.Indentation;
import ai.metaheuristic.ww2003.document.tags.Shadowed;
import org.jspecify.annotations.Nullable;

public class Para
        extends Composite
        implements XmlTag, HasProperty, Indentation, Shadowed {

    private static final String NS = "w";
    private static final String TAG_NAME = "p";

    @Nullable
    public Integer indent = null;
    public boolean isShadow = false;

    @Nullable
    @Override
    public Integer getIndent() {
        return indent;
    }

    @Override
    public void setIndent(@Nullable Integer indent) {
        this.indent = indent;
    }

    @Override
    public boolean isShadow() {
        return isShadow;
    }

    @Override
    public void setShadow(boolean shadow) {
        this.isShadow = shadow;
    }

    public Para() {
        super();
    }

    public Para(Enums.Align align, CDNode... nodes) {
        this(align, null, nodes);
    }

    public Para(Enums.Align align, @Nullable Integer indent, CDNode... nodes) {
        super(nodes);
        setAlign(align);
        setIndent(indent);
    }

    public Para(CDNode... nodes) {
        super(nodes);
    }

    @Override
    public String getNameSpace() {
        return NS;
    }

    @Override
    public String getTagName() {
        return TAG_NAME;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        streamNodes().forEach(node -> builder.append(node.toString()));
        return builder.toString();
    }

    public static Para rt(String text) {
        return new Para(Run.t(text));
    }
}
