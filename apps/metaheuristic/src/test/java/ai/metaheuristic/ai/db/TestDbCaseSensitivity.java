/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.db;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 6/11/2021
 * Time: 3:19 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestDbCaseSensitivity extends PreparingSourceCode {

    private static final String TEST_VARIABLE = "test-variable";

    @Autowired
    private DispatcherParamsTopLevelService dispatcherParamsTopLevelService;

    @Autowired
    private GlobalVariableRepository globalVariableRepository;

    @SneakyThrows
    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void test() {

        assertNotEquals(TEST_VARIABLE, TEST_VARIABLE.toUpperCase());

        ExecContextCreatorService.ExecContextCreationResult result = createExecContextForTest();
        assertNull(result.errorMessages, ""+result.errorMessages);
        execContextForTest = result.execContext;

        Variable v = variableService.createUninitialized(TEST_VARIABLE, execContextForTest.id, Consts.TOP_LEVEL_CONTEXT_ID);
        assertNotNull(v);

        assertNotNull(variableRepository.findByNameAndTaskContextIdAndExecContextId(
                TEST_VARIABLE, Consts.TOP_LEVEL_CONTEXT_ID, execContextForTest.id));

        assertNull(variableRepository.findByNameAndTaskContextIdAndExecContextId(
                TEST_VARIABLE.toUpperCase(), Consts.TOP_LEVEL_CONTEXT_ID, execContextForTest.id));

        assertNotEquals(GLOBAL_TEST_VARIABLE, GLOBAL_TEST_VARIABLE.toUpperCase());
        assertNotNull(globalVariableRepository.findIdByName(GLOBAL_TEST_VARIABLE));
        assertNull(globalVariableRepository.findIdByName(GLOBAL_TEST_VARIABLE.toUpperCase()));

        assertNotEquals(sourceCode.uid, sourceCode.uid.toUpperCase());
        assertNotNull(sourceCodeRepository.findIdByUid(sourceCode.uid));
        assertNull(sourceCodeRepository.findIdByUid(sourceCode.uid.toUpperCase()));

        assertNotEquals(f1.code, f1.code.toUpperCase());
        assertNotNull(functionRepository.findByCode(f1.code));
        assertNull(functionRepository.findByCode(f1.code.toUpperCase()));
    }
}
