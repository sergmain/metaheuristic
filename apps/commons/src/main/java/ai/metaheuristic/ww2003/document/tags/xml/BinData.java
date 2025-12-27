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

import ai.metaheuristic.ww2003.document.Leaf;
import lombok.NoArgsConstructor;
import javax.annotation.Nullable;

import java.util.List;

@NoArgsConstructor
public class BinData extends Leaf implements XmlTag, TextContainer {

    private static final String NS = "w";
    private static final String TAG_NAME = "binData";

    public StringBuilder text = new StringBuilder();

    public BinData(Attr... attrs) {
        super(attrs);
    }

    public BinData(List<Attr> attrs) {
        super(attrs);
    }

    @Override
    public void setText(String text) {
        this.text = new StringBuilder(text);
    }

    @Override
    public String getText() {
        return text.toString();
    }


    public BinData concat(String str) {
        text.append(str);
        return this;
    }

    @Nullable
    @Override
    public String tag() {
        return null;
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
