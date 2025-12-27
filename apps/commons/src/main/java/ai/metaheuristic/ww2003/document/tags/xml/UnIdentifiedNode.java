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

import ai.metaheuristic.ww2003.document.Composite;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UnIdentifiedNode extends Composite implements XmlTag {

    @Nullable
    public final String nameSpace;
    public final String tagName;

    public UnIdentifiedNode(@Nullable String nameSpace, String tagName, Attr... attrs) {
        this.nameSpace = nameSpace;
        this.tagName = tagName;
        if (attrs.length > 0) {
            attributes = new ArrayList<>(List.of(attrs));
        }
    }

    @Nullable
    @Override
    public String getNameSpace() {
        return nameSpace;
    }

    @Override
    public String getTagName() {
        return tagName;
    }

}