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
package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.ProcessorCore;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public abstract class PreparingCore {

    @Autowired private Globals globals;
    @Autowired private PreparingCoreInitService preparingCoreService;

    public PreparingData.PreparingCodeData preparingCodeData;

    public Processor getProcessor() {
        return preparingCodeData.processor;
    }

    public ProcessorCore getCore1() {
        return preparingCodeData.core1;
    }

    @Nullable
    public Function getFitFunction() {
        return preparingCodeData.fitFunction;
    }

    @Nullable
    public Function getPredictFunction() {
        return preparingCodeData.predictFunction;
    }

    @BeforeEach
    public void beforePreparingCore() {
        assertTrue(globals.testing);
        preparingCodeData = preparingCoreService.beforePreparingCore();
    }

    @AfterEach
    public void afterPreparingCore() {
        try {
            preparingCoreService.afterPreparingCore(preparingCodeData);
        }
        catch (Throwable th) {
            log.error("Error", th);
        }
    }
}
