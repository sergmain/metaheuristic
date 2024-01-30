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

package ai.metaheuristic.ww2003.document.tags.xml.table;

import ai.metaheuristic.ww2003.document.Leaf;
import ai.metaheuristic.ww2003.document.tags.PropertyElement;
import ai.metaheuristic.ww2003.document.tags.xml.Attr;
import ai.metaheuristic.ww2003.document.tags.xml.XmlTag;
import lombok.NoArgsConstructor;
import javax.annotation.Nullable;

import java.util.List;

/*
This element specifies that this cell is part of a vertically merged set of cells in a table.
The val attribute on this element determines how this cell is defined
with respect to the previous cell in the table
(i.e., whether this cell continues the vertical merge or starts a new merged group of cells).

https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.wordprocessing.verticalmerge?view=openxml-2.8.1
*/
@NoArgsConstructor
public class VMerge extends Leaf implements XmlTag, PropertyElement {

    private static final String NS = "w";
    private static final String TAG_NAME = "vmerge";

    public VMerge(List<Attr> attrs) {
        super(attrs);
    }

    public VMerge(Attr... attrs) {
        super(attrs);
    }

    @Nullable
    @Override
    public String tag() {
        return "<w:vmerge/>";
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
