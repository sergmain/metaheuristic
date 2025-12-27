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

package ai.metaheuristic.ww2003.document.presentation;

import org.jspecify.annotations.Nullable;

public enum HighlightColor {

    // code of colors - https://learn.microsoft.com/en-us/power-platform/power-fx/reference/function-colors
    EDITION("#FDB900", new String[]{"yellow"}),
    EXPIRED("#A9A9A9", new String[]{"dark-gray", "darkgray"}),
    NON_PROCESSED("#008000", new String[]{"green"}),
    DELETION_CANDIDATE("#FF0000", new String[]{"red"}),
    ERROR("#00FFFF", new String[]{"cyan"}),
    WORDS_EXCLUSION("#D3D3D3", new String[]{"light-gray", "lightgray"}),
    MAROON("#800000", new String[]{"maroon"}),
    BLACK("#000000", new String[]{"black"}),
    FUCHSIA("#FF00FF", new String[]{"fuchsia"}),
    ;

    public final String[] colorName;
    public final String colorValue;

    HighlightColor(String colorValue, String[] colorName) {
        this.colorName = colorName;
        this.colorValue = colorValue;
    }

    public String getColorName() {
        return colorName[0];
    }

    @Nullable
    public static HighlightColor asHighlightColor(@Nullable String val) {
        if (val==null || val.length()==0) {
            return null;
        }
        String finalVal;
        if (val.charAt(0)!='#') {
            finalVal = '#' + "0".repeat(val.length() < 7 ? 6 - val.length() : 0) + val;
        }
        else {
            finalVal = val;
        }

        for (HighlightColor value : HighlightColor.values()) {
            for (String name : value.colorName) {
                if (name.equalsIgnoreCase(val)) {
                    return value;
                }
            }
            if (value.colorValue.equalsIgnoreCase(finalVal)) {
                return value;
            }
        }
        return null;
    }
}