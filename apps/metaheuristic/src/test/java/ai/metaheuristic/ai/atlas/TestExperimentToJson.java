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
import ai.metaheuristic.ai.launchpad.atlas.AtlasService;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentTopLevelService;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.ai.yaml.atlas.AtlasParamsYamlUtils;
import ai.metaheuristic.ai.yaml.atlas.AtlasParamsYamlWithCache;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.plan.PlanApiData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestExperimentToJson extends PreparingPlan {

    @Autowired
    private ExperimentTopLevelService experimentTopLevelService;

    @Autowired
    private AtlasService atlasService;

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Override
    public String getPlanYamlAsString() {
        return getPlanParamsYamlAsString_Simple();
    }

    @Test
    public void toJson() throws JsonProcessingException {

        long experimentId = experiment.getId();
        ExperimentApiData.ExperimentInfoExtendedResult result =
                experimentTopLevelService.getExperimentInfo(experimentId);

        String json = mapper.writeValueAsString(result);

        System.out.println("json =\n" + json);
    }

    @Test
    public void toExperimentStoredToAtlasToYaml() throws IOException {

        //noinspection unused
        PlanApiData.TaskProducingResultComplex result = produceTasksForTest();

        assertNotNull(experiment);
        assertNotNull(experiment.getId());
        assertNotNull(experiment.getWorkbookId());

        long experimentId = experiment.getId();

        AtlasService.StoredToAtlasWithStatus r = atlasService.toExperimentStoredToAtlas(experimentId);
        assertEquals(Enums.StoringStatus.OK, r.status);

        String yaml = AtlasParamsYamlUtils.BASE_YAML_UTILS.toString(r.atlasParamsYamlWithCache.atlasParams);

        System.out.println("yaml =\n" + yaml);
        AtlasParamsYamlWithCache atywc = new AtlasParamsYamlWithCache(AtlasParamsYamlUtils.BASE_YAML_UTILS.to(yaml));
        System.out.println("atywc = " + atywc);

        // TODO 2019-07-13 add here comparisons of r.atlasParamsYamlWithCache.atlasParams and atywc
    }
}
