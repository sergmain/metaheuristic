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

package ai.metaheuristic.ww2003.document.tags.xml;

import ai.metaheuristic.ww2003.document.Leaf;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
public class Tab extends Leaf implements XmlTag {

    private static final String NS = "w";
    private static final String TAG_NAME = "tab";

    public Tab(List<Attr> attrs) {
        super(attrs);
    }

    public Tab(Attr... attrs) {
        super(attrs);
    }

    @Override
    public String tag() {
        return "<w:tab/>";
    }

    @Override
    public String getNameSpace() {
        return NS;
    }

    @Override
    public String getTagName() {
        return TAG_NAME;
    }
}
