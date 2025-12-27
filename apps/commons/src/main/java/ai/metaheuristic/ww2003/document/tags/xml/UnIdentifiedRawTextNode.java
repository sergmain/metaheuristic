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

import javax.annotation.Nullable;

/**
 * @author Sergio Lissner
 * Date: 9/9/2022
 * Time: 4:48 AM
 */
public class UnIdentifiedRawTextNode extends UnIdentifiedTextNode implements RawText {

    public UnIdentifiedRawTextNode(@Nullable String nameSpace, String tagName) {
        super(nameSpace, tagName);
    }
}
