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

package ai.metaheuristic.ai.experiment_result;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultService;
import ai.metaheuristic.ai.preparing.PreparingExperiment;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsJsonUtils;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsYamlWithCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestExperimentToJson extends PreparingExperiment {

    @Autowired private ExperimentResultService experimentResultService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void toExperimentStoredToExperimentResultToYaml() {
        createExperiment();

        preparingSourceCodeService.produceTasksForTest(getSourceCodeYamlAsString(), preparingSourceCodeData);

        assertNotNull(getExperiment());
        assertNotNull(getExperiment().getId());
//        assertNotNull(experiment.getExecContextId());

        ExperimentResultService.StoredToExperimentResultWithStatus r = ExperimentResultService.toExperimentStoredToExperimentResult(getExecContextForTest().asSimple(), getExperiment());
        assertEquals(Enums.StoringStatus.OK, r.status);

        String yaml = ExperimentResultParamsJsonUtils.BASE_UTILS.toString(r.experimentResultParamsYamlWithCache.experimentResult);

        System.out.println("yaml =\n" + yaml);
        ExperimentResultParamsYamlWithCache erpywc = new ExperimentResultParamsYamlWithCache(ExperimentResultParamsJsonUtils.BASE_UTILS.to(yaml));
        System.out.println("erpywc = " + erpywc);

        // TODO 2019-07-13 add here comparisons of ExperimentResultParamsYamlWithCache and erpywc
    }
}
