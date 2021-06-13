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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.preparing.PreparingExperiment;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 6/11/2021
 * Time: 3:19 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
@DirtiesContext
@AutoConfigureCache
public class TestExecSourceCode extends PreparingSourceCode {

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void testFeatures() throws IOException {

        String sc = IOUtils.resourceToString("/source_code/yaml/default-source-code-for-testing.yaml", StandardCharsets.UTF_8)

        String params = getSourceCodeYamlAsString();
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(params);
        sourceCodeParamsYaml.checkIntegrity();

        cleanUp(sourceCodeParamsYaml.source.uid);

        company = new Company();
        company.name = "Test company #2";
        companyTopLevelService.addCompany(company);
        company = Objects.requireNonNull(companyTopLevelService.getCompanyByUniqueId(company.uniqueId));

        assertNotNull(company.id);
        assertNotNull(company.uniqueId);

        // id==1L must be assigned only to master company
        assertNotEquals(Consts.ID_1, company.id);

        f1 = createFunction("function-01:1.1");
        f2 = createFunction("function-02:1.1");
        f3 = createFunction("function-03:1.1");
        f4 = createFunction("function-04:1.1");
        f5 = createFunction("function-05:1.1");

        SourceCodeApiData.SourceCodeResult scr = sourceCodeTopLevelService.createSourceCode(getSourceCodeYamlAsString(), company.uniqueId);
        sourceCode = Objects.requireNonNull(sourceCodeCache.findById(scr.id));
        long mills = System.currentTimeMillis();
        log.info("Start experimentService.produceFeaturePermutations()");

        produceTasksForTest();
        log.info("experimentService.produceFeaturePermutations() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start experimentFeatureRepository.findByExperimentId()");
        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        log.info("experimentFeatureRepository.findByExperimentId() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        String s = "feature-per-task";
        // todo 2020-03-12 right now permutation is being created dynamically at runtime.
        //  so for calculation an actual number of permutation we need to process all tasks in current SourceCode/ExecContext
/*
        assertNotNull(epy.processing.features);
        assertEquals(7, epy.processing.features.size());
*/
    }
}
