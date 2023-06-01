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

import ai.metaheuristic.ww2003.document.exceptions.DocumentProcessingException;
import org.springframework.lang.Nullable;

/**
 * @author Serge
 * Date: 9/29/2019
 * Time: 12:08 PM
 */
public class Enums {

    public enum Place {left, middle, right }

    public enum ShadowColorScheme { normal, vst }

    public enum Indent {
        none("-1", -1), indent_0("0", 0), indent_540("540", 540), indent_283("283", 283);
        public final String indent;
        public final int indentInt;

        Indent(String indent, int indentInt) {
            this.indent = indent;
            this.indentInt = indentInt;
        }

        public static Indent to(@Nullable String indent) {
            if (indent==null) {
                return none;
            }
            return switch (indent) {
                case "0" -> indent_0;
                case "540" -> indent_540;
                case "283" -> indent_283;
                default -> throw new DocumentProcessingException("214.008 unknown indent: " + indent);
            };
        }
    }

    public enum Align {
        none("none"), left("left"), center("center"), right("right"), both("both");
        public final String align;

        Align(String align) {
            this.align = align;
        }

        public static Align to(@Nullable String alignment) {
            if (alignment==null) {
                return none;
            }
            return switch (alignment) {
                case "left", "start" -> left;
                case "center" -> center;
                case "right", "end" -> right;
                case "both", "justify", "distribute" -> both;
                case "none" -> none;
                default -> throw new DocumentProcessingException("214.010 unknown alignment: " + alignment);
            };
        }
    }

    public enum ContinueStrategy { stop, non_stop }

}
