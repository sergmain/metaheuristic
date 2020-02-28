/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.atlas;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.atlas.AtlasService;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentTopLevelService;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.ai.yaml.atlas.AtlasParamsYamlUtils;
import ai.metaheuristic.ai.yaml.atlas.AtlasParamsYamlWithCache;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
public class TestExperimentToJson extends PreparingPlan {

    @Autowired
    private ExperimentTopLevelService experimentTopLevelService;

    @Autowired
    private AtlasService atlasService;

    @Autowired
    private AtlasParamsYamlUtils atlasParamsYamlUtils;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void toExperimentStoredToAtlasToYaml() {

        //noinspection unused
        SourceCodeApiData.TaskProducingResultComplex result = produceTasksForTest();

        assertNotNull(experiment);
        assertNotNull(experiment.getId());
        assertNotNull(experiment.getExecContextId());

        AtlasService.StoredToAtlasWithStatus r = atlasService.toExperimentStoredToAtlas(sourceCode, execContextForFeature, experiment);
        assertEquals(Enums.StoringStatus.OK, r.status);

        String yaml = atlasParamsYamlUtils.BASE_YAML_UTILS.toString(r.atlasParamsYamlWithCache.atlasParams);

        System.out.println("yaml =\n" + yaml);
        AtlasParamsYamlWithCache atywc = new AtlasParamsYamlWithCache(atlasParamsYamlUtils.BASE_YAML_UTILS.to(yaml));
        System.out.println("atywc = " + atywc);

        // TODO 2019-07-13 add here comparisons of r.atlasParamsYamlWithCache.atlasParams and atywc
    }
}
