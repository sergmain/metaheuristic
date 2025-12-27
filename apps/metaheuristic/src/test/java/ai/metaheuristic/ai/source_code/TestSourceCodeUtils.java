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

package ai.metaheuristic.ai.source_code;

import org.junit.jupiter.api.Test;

import static ai.metaheuristic.ai.dispatcher.source_code.SourceCodeUtils.isVariableNameOk;
import static ai.metaheuristic.api.EnumsApi.SourceCodeValidateStatus.OK;
import static ai.metaheuristic.api.EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_VARIABLE_NAME_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 4/4/2021
 * Time: 4:49 PM
 */
public class TestSourceCodeUtils {

    @Test
    public void test() {
        assertEquals(OK, isVariableNameOk("aaa"));
        assertEquals(OK, isVariableNameOk("Aaa"));
        assertEquals(OK, isVariableNameOk("Aaa"));
        assertEquals(OK, isVariableNameOk("Aaa-"));
        assertEquals(OK, isVariableNameOk("_aaa_"));
        assertEquals(OK, isVariableNameOk("_aaa"));
        assertEquals(OK, isVariableNameOk("_aaa"));
        assertEquals(OK, isVariableNameOk("_aaa."));
        assertEquals(OK, isVariableNameOk("_aaa.1"));
        assertEquals(OK, isVariableNameOk("1"));
        assertEquals(OK, isVariableNameOk("1a"));

        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("\\aaa.1"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("/aaa.1"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("aaa\\aaa.1"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("aaa/aaa.1"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk(" "));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a a"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a,a"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a 1"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk(".a1"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("1 "));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("!a"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a!"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a@"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a#"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a$"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a%"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a^"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a&"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a*"));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a("));
        assertEquals(WRONG_FORMAT_OF_VARIABLE_NAME_ERROR, isVariableNameOk("a)"));
    }
}
