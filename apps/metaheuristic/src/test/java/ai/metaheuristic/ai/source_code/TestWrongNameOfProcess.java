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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 3/14/2020
 * Time: 8:53 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles({"dispatcher", "mysql"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TestWrongNameOfProcess extends PreparingSourceCode {

    @Autowired private SourceCodeValidationService sourceCodeValidationService;

    @Override
    @SneakyThrows
    public String getSourceCodeYamlAsString() {
        return IOUtils.resourceToString("/source_code/yaml/for-testing-wrong-name-of-process.yaml", StandardCharsets.UTF_8);
    }

    @Test
    public void test() {
        SourceCodeApiData.SourceCodeValidationResult status = sourceCodeValidationService.checkConsistencyOfSourceCode(getSourceCode());
        assertEquals(EnumsApi.SourceCodeValidateStatus.WRONG_CODE_OF_PROCESS_ERROR, status.status);
    }
}



