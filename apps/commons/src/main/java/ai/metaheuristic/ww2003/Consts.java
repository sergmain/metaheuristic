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

package ai.metaheuristic.ww2003;

import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class Consts {

    public static final String ENDING_DASHES = "------------------------------------------------------------------";
    public static final String SPACE_DASHES = "- - - - - - - - - - - - - - - - - - - - - - - - - -";
    public static final String INITIAL_MARK_NUMBER = "000000";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final String WHITE_COLOR = "FFFFFF";
    public static final int EMPTY_PARAGRAPH_COUNT = 3;
    public static final int EMPTY_PARAGRAPH_BEFORE_ATTACHMENT_COUNT = 5;

    public static final int INDENT_FIRST_LINE = 540;

    public static final int MAX_EDITION_STRING_LENGTH = 75;

    public static final char UPPER_RIGHT_CORNER_CHAR = '┐';
    public static final char UPPER_LEFT_CORNER_CHAR = '┌';
    public static final char LOWER_LEFT_CORNER_CHAR = '└';
    public static final char LOWER_RIGHT_CORNER_CHAR = '┘';
    public static final Pattern P_LF = Pattern.compile("\n");

}
