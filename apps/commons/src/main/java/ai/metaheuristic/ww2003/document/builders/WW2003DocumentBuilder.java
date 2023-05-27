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

package ai.metaheuristic.ww2003.document.builders;

import ai.metaheuristic.ww2003.document.WW2003Document;
import ai.metaheuristic.ww2003.document.ThreadLocalUtils;
import ai.metaheuristic.ww2003.document.tags.xml.Body;
import ai.metaheuristic.ww2003.document.tags.xml.Sect;
import ai.metaheuristic.ww2003.document.tags.xml.Styles;

public class WW2003DocumentBuilder {

    private Styles styles;

    public WW2003Document build() {
        Sect sect = new Sect();
        Body body = new Body(sect);
        WW2003Document ww2003Document = new WW2003Document();
        if (styles != null) {
            ww2003Document.add(styles);
        }
        ww2003Document.add(body);

        if (styles != null) {
            ThreadLocalUtils.getInnerStyles().initStyles(ww2003Document.getNodes());
        }
        return ww2003Document;
    }

    public WW2003DocumentBuilder setStyles(Styles styles) {
        this.styles = styles;
        return this;
    }
}