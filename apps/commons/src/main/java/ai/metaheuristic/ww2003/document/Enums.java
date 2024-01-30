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

package ai.metaheuristic.ww2003.document;

import lombok.RequiredArgsConstructor;

public class Enums {

    public enum BypassDirection {
        FORWARD,
        BACKWARD
    }

    @RequiredArgsConstructor
    public enum ConsPStyle {
        CONS_NORMAL("ConsNormal", "1"),
        CONS_NON_FORMAT("ConsNonformat", "2"),
        CONS_DT_NORMAL("ConsDTNormal", "3"),
        CONS_DT_NON_FORMAT("ConsDTNonformat", "4");

        public final String styleName;
        public final String styleId;
    }


    public enum Colors {
        dark_red("#800000");

        public final String color;

        Colors(String color) {
            this.color = color;
        }
    }

    public enum Relation {
        CHILD,
        DESCENDANT,
        ALL_DESCENDANTS
    }
}
