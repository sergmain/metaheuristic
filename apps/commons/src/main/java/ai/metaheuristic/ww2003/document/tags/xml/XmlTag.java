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

package ai.metaheuristic.ww2003.document.tags.xml;

import ai.metaheuristic.commons.S;

import org.springframework.lang.Nullable;

public interface XmlTag {

    @Nullable
    String getNameSpace();

    String getTagName();

    default String openTag() {
        String nameSpace = getNameSpace();
        if (S.b(nameSpace)) {
            return '<' + getTagName() + '>';
        }
        return '<' + getNameSpace() + ':' + getTagName() + '>';
    }

    default String closeTag() {
        String nameSpace = getNameSpace();
        if (S.b(nameSpace)) {
            return "</" + getTagName() + '>';
        }
        return "</" + getNameSpace() + ':' + getTagName() + '>';
    }

}
