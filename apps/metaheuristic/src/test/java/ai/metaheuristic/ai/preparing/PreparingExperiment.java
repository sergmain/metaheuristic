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

package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Serge
 * Date: 9/27/2020
 * Time: 8:52 PM
 */
@Slf4j
public abstract class PreparingExperiment extends PreparingSourceCode {

    @Autowired private PreparingExperimentInitService preparingExperimentService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;

    private Experiment experiment = null;

    @BeforeEach
    public void beforePreparingExperiment() {
    }

    public void createExperiment() {
        ExecContextCreatorService.ExecContextCreationResult r = preparingSourceCodeService.createExecContextForTest(preparingSourceCodeData);
        assertNull(r.errorMessages, ""+r.errorMessages);
        assertNotNull(r.execContext);
        setExecContextForTest(r.execContext);

        experiment = preparingExperimentService.createExperiment(getExecContextForTest());
    }

    @AfterEach
    public void afterPreparingExperiment() {
        long mills = System.currentTimeMillis();
        log.info("Start afterPreparingExperiment()");
        if (getExperiment() != null) {
            preparingExperimentService.afterPreparingExperiment(getExperiment());
        }
        log.info("afterPreparingExperiment() was finished for {} milliseconds", System.currentTimeMillis() - mills);
    }

    public Experiment getExperiment() {
        return experiment;
    }
}
