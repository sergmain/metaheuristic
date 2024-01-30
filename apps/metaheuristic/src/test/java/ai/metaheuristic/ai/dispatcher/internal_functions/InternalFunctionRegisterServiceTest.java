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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.mhbp.data.ScenarioData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sergio Lissner
 * Date: 8/28/2023
 * Time: 1:52 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
public class InternalFunctionRegisterServiceTest {

    @Autowired
    private InternalFunctionRegisterService internalFunctionRegisterService;

    @Test
    public void test_cachable_functions() {
        assertTrue(internalFunctionRegisterService.getCachableFunctions().contains(Consts.MH_API_CALL_FUNCTION));
    }

    @Test
    public void test_initialization() {
        assertNotNull(internalFunctionRegisterService.get(Consts.MH_AGGREGATE_FUNCTION));
        assertNotNull(internalFunctionRegisterService.get(Consts.MH_ACCEPTANCE_TEST_FUNCTION));
        assertNotNull(internalFunctionRegisterService.get(Consts.MH_BATCH_LINE_SPLITTER_FUNCTION));
        assertNotNull(internalFunctionRegisterService.get(Consts.MH_ENHANCE_TEXT_FUNCTION));
    }

    @Test
    public void test_getScenarioCompatibleFunctions() {
        final List<ScenarioData.InternalFunction> functions = internalFunctionRegisterService.getScenarioCompatibleFunctions();
        assertNotNull(functions.stream().filter(f->f.getCode().equals(Consts.MH_AGGREGATE_FUNCTION)).findFirst().orElse(null));
        assertNotNull(functions.stream().filter(f->f.getCode().equals(Consts.MH_ACCEPTANCE_TEST_FUNCTION)).findFirst().orElse(null));
        assertNotNull(functions.stream().filter(f->f.getCode().equals(Consts.MH_BATCH_LINE_SPLITTER_FUNCTION)).findFirst().orElse(null));
        assertNotNull(functions.stream().filter(f->f.getCode().equals(Consts.MH_ENHANCE_TEXT_FUNCTION)).findFirst().orElse(null));
    }
}
