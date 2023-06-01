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

package ai.metaheuristic.ww2003;

import ai.metaheuristic.ww2003.document.WW2003Document;
import ai.metaheuristic.ww2003.document.style.WW2003DocumentStylesUtils;
import ai.metaheuristic.ww2003.document.tags.xml.Body;
import ai.metaheuristic.ww2003.document.tags.xml.Sect;

/**
 * @author Sergio Lissner
 * Date: 5/27/2023
 * Time: 10:43 PM
 */
public class CreateWW2003Document {

    public static WW2003Document createWW2003Document() {
        WW2003Document ww2003Document = new WW2003Document();
        return addDefaultTags(ww2003Document);
    }

    public static WW2003Document addDefaultTags(WW2003Document ww2003Document) {
        ww2003Document.attributes = WW2003Document.createDefaultDocAttrs();
        ww2003Document.add(WW2003Document.createDefaultFonts());
        ww2003Document.add(WW2003DocumentStylesUtils.createDefaultStyles());
        ww2003Document.add(WW2003Document.createDefaultDocPr());
        ww2003Document.add(new Body(new Sect(WW2003Document.createDefaultSectProp())));
        return ww2003Document;
    }
}
