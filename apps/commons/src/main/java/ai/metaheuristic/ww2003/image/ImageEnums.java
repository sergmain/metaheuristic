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

package ai.metaheuristic.ww2003.image;

import ai.metaheuristic.commons.S;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Serge
 * Date: 11/8/2019
 * Time: 6:21 PM
 */
public class ImageEnums {

    public enum Units { Unknown, PixelsPerCentimeter, PixelsPerInch, Undefined }

    public enum Type {
        UNKNOWN(),
        BMP("bmp"),
        GIF("gif"),
        JPG("jpg", "JPEG", "JPG"),
        PNG("png"),
        WMF("wmf"),
        WMZ("wmz");

        private final Set<String> types = new HashSet<>();

        Type(String... type) {
            Collections.addAll(this.types, type);
        }

        public static Type to(String s) {
            if (S.b(s)) {
                return UNKNOWN;
            }
            String t = (s.startsWith(".")) ? s.substring(1) : s;

            for (Type type : Type.values()) {
                if (type.types.contains(t)) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }
}
