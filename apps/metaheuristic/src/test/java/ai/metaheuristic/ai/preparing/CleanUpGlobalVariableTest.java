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
package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.MhSharedItTest;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableTxService;
import ai.metaheuristic.commons.spi.GeneralBlobTxService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static ai.metaheuristic.ai.preparing.PreparingConsts.GLOBAL_TEST_VARIABLE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization test for PreparingSourceCodeService.cleanUp().
 * cleanUp() is a leftover V2 teardown that deletes the shared (V3) global-test-variable row,
 * which leaves the static sharedSourceInfra cache stale and makes other tests flaky.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
@Slf4j
public class CleanUpGlobalVariableTest extends MhSharedItTest {

    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private GeneralBlobTxService variableBlobTxService;
    @Autowired private GlobalVariableTxService globalVariableService;
    @Autowired private GlobalVariableRepository globalVariableRepository;

    @Test
    public void cleanUp_mustPreserveSharedGlobalTestVariable() {
        // make the shared global var present; remember whether THIS test created it
        final boolean createdHere = globalVariableRepository.findIdByName(GLOBAL_TEST_VARIABLE) == null;
        if (createdHere) {
            variableBlobTxService.createEmptyGlobalVariable(GLOBAL_TEST_VARIABLE, "file-01.txt");
        }
        assertNotNull(globalVariableRepository.findIdByName(GLOBAL_TEST_VARIABLE));

        try {
            // a uid that matches no SourceCode, so only cleanUp's global-variable deletion path runs
            preparingSourceCodeService.cleanUp("no-such-source-code-" + System.currentTimeMillis());

            // desired behavior: cleanUp must NOT delete the shared (V3) global-test-variable
            assertNotNull(globalVariableRepository.findIdByName(GLOBAL_TEST_VARIABLE));
        } finally {
            // hermetic: NAME is UNIQUE and buildSharedSourceInfra() inserts unconditionally, so leaving
            // a row would break a later preparing test's build. Remove it only if WE created it; if it
            // pre-existed (shared infra), leave it so later tests still see it.
            if (createdHere) {
                globalVariableService.deleteByVariable(GLOBAL_TEST_VARIABLE);
            }
        }
    }
}
