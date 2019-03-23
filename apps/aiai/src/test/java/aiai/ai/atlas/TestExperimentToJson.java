/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.atlas;

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.atlas.AtlasService;
import aiai.ai.launchpad.atlas.ExperimentStoredToAtlas;
import aiai.ai.launchpad.data.ExperimentData;
import aiai.ai.launchpad.experiment.ExperimentTopLevelService;
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

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestExperimentToJson {

    @Autowired
    private ExperimentTopLevelService experimentTopLevelService;

    @Autowired
    private AtlasService atlasService;

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    public static class StoredExperiment {
        Experiment experiment;
    }

    @Test
    public void toJson() throws JsonProcessingException {

        long experimentId = 224;
        ExperimentData.ExperimentInfoExtendedResult result =
                experimentTopLevelService.getExperimentInfo(experimentId);

        String json = mapper.writeValueAsString(result);

        System.out.println("json =\n" + json);
        Experiment e = null;
    }

    @Test
    public void toExperimentStoredToAtlasToJson() throws IOException {

        long experimentId = 224;

        AtlasService.StoredToAtlasWithStatus r = atlasService.toExperimentStoredToAtlas(experimentId);
        if (r.status!= Enums.StoringStatus.OK) {
            throw new IllegalStateException("experiment can't be stored");
        }
        String json = atlasService.toJson(r.experimentStoredToAtlas);

        System.out.println("json =\n" + json);
        ExperimentStoredToAtlas estb1 = mapper.readValue(json, ExperimentStoredToAtlas.class);
        System.out.println("estb1 = " + estb1);
    }
}
