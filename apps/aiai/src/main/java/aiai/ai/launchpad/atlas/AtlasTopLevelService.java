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

package aiai.ai.launchpad.atlas;

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.Atlas;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.ExperimentHyperParams;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.data.AtlasData;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.experiment.ExperimentUtils;
import aiai.ai.launchpad.repositories.AtlasRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;

@SuppressWarnings("Duplicates")
@Slf4j
@Service
public class AtlasTopLevelService {

    private final AtlasRepository atlasRepository;
    private final AtlasService atlasService;

    public AtlasTopLevelService(AtlasRepository atlasRepository, AtlasService atlasService) {
        this.atlasRepository = atlasRepository;
        this.atlasService = atlasService;
    }

    public AtlasData.ExperimentInfoExtended getExperimentInfo(Long id) {

        Atlas atlas = atlasRepository.findById(id).orElse(null);
        if (atlas == null) {
            return new AtlasData.ExperimentInfoExtended("#280.09 experiment wasn't found in atlas, id: " + id);
        }

        ExperimentStoredToAtlas estb1;
        try {
            estb1 = atlasService.fromJson(atlas.experiment);
        } catch (IOException e) {
            log.error("Error", e);
            return new AtlasData.ExperimentInfoExtended("#280.09 Can't extract experiment from atlas, error: " + e.toString());
        }

        Experiment experiment = estb1.experiment;
        if (experiment == null) {
            return new AtlasData.ExperimentInfoExtended("#280.09 experiment wasn't found, experimentId: " + id);
        }
        if (experiment.getFlowInstanceId() == null) {
            return new AtlasData.ExperimentInfoExtended("#280.12 experiment wasn't startet yet, experimentId: " + id);
        }
        FlowInstance flowInstance = estb1.flowInstance;
        if (flowInstance == null) {
            return new AtlasData.ExperimentInfoExtended("#280.16 experiment has broken ref to flowInstance, experimentId: " + id);
        }

        for (ExperimentHyperParams hyperParams : estb1.getHyperParams()) {
            if (StringUtils.isBlank(hyperParams.getValues())) {
                continue;
            }
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParams.getValues());
            hyperParams.setVariants(variants.status ? variants.count : 0);
        }

        AtlasData.ExperimentInfoExtended result = new AtlasData.ExperimentInfoExtended();
        if (experiment.getFlowInstanceId() == null) {
            result.addInfoMessage("Launch is disabled, dataset isn't assigned");
        }

        AtlasData.ExperimentInfo experimentInfoResult = new AtlasData.ExperimentInfo();
        experimentInfoResult.features = estb1.features;
        experimentInfoResult.flowInstance = flowInstance;
        experimentInfoResult.flowInstanceExecState = Enums.FlowInstanceExecState.toState(flowInstance.execState);

        result.experiment = experiment;
        result.experimentInfo = experimentInfoResult;
        return result;
    }

    public OperationStatusRest experimentDeleteCommit(Long id) {
        Atlas atlas = atlasRepository.findById(id).orElse(null);
        if (atlas == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "#280.71 experiment wasn't found in atlas, id: " + id);
        }
        atlasRepository.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }
}
