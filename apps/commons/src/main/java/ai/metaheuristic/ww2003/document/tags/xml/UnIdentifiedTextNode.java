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
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

public class UnIdentifiedTextNode extends Composite implements XmlTag, TextContainer {

    @Setter
    @Getter
    String text;

    @Nullable
    public final String nameSpace;
    public final String tagName;

    public UnIdentifiedTextNode(@Nullable String nameSpace, String tagName) {
        this.nameSpace = nameSpace;
        this.tagName = tagName;
        text = "";
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

    @Override
    public TextContainer concat(String text) {
        this.text += text;
        return this;
    }
}