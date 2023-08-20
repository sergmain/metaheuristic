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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.api.EnumsApi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceCodeUtils {

    private static final Pattern VARIABLE_NAME_CHARS_PATTERN = Pattern.compile("^[A-Za-z0-9_-][A-Za-z0-9._-]*$");

    public static EnumsApi.SourceCodeValidateStatus isVariableNameOk(String name) {
        Matcher m = VARIABLE_NAME_CHARS_PATTERN.matcher(name);
        return m.matches() ? EnumsApi.SourceCodeValidateStatus.OK : EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_VARIABLE_NAME_ERROR;
    }


}
