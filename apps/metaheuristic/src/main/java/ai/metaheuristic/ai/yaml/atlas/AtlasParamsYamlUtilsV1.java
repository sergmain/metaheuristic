/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.yaml.atlas;

import ai.metaheuristic.ai.launchpad.beans.AtlasTask;
import ai.metaheuristic.ai.launchpad.repositories.AtlasTaskRepository;
import ai.metaheuristic.api.data.atlas.AtlasParamsYamlV1;
import ai.metaheuristic.api.data.atlas.AtlasParamsYamlV2;
import ai.metaheuristic.api.data.atlas.AtlasTaskParamsYaml;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 11:36 PM
 */
@Service
@Profile("launchpad")
@RequiredArgsConstructor
public class AtlasParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<AtlasParamsYamlV1, AtlasParamsYamlV2, AtlasParamsYamlUtilsV2, Void, Void, Void> {

    private final AtlasTaskRepository atlasTaskRepository;
    private final AtlasParamsYamlUtilsV2 atlasParamsYamlUtilsV2;

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(AtlasParamsYamlV1.class);
    }

    @Override
    public AtlasParamsYamlV2 upgradeTo(AtlasParamsYamlV1 src, Long ... vars) {
        if (vars==null || vars.length==0) {
            throw new IllegalStateException("Not enough parameters");
        }
        src.checkIntegrity();
        AtlasParamsYamlV2 trg = new AtlasParamsYamlV2();
        trg.createdOn = src.createdOn;
        trg.plan = new AtlasParamsYamlV2.PlanWithParamsV2(src.plan.planId, src.plan.planParams);
        trg.workbook = new AtlasParamsYamlV2.WorkbookWithParamsV2(src.workbook.workbookId, src.workbook.workbookParams, src.workbook.execState);
        trg.experiment = new AtlasParamsYamlV2.ExperimentWithParamsV2(src.experiment.experimentId, src.experiment.experimentParams);
        trg.taskIds = src.tasks.stream().peek(t->{
            final Long atlasId = vars[0];
            AtlasTask at = atlasTaskRepository.findByAtlasIdAndTaskId(atlasId, t.taskId);
            if (at==null) {
                at = new AtlasTask();
                at.atlasId = atlasId;
                at.taskId = t.taskId;
                AtlasTaskParamsYaml atpy = new AtlasTaskParamsYaml();
                atpy.assignedOn = t.getAssignedOn();
                atpy.completed = t.isCompleted();
                atpy.completedOn = t.getCompletedOn();
                atpy.execState = t.getExecState();
                atpy.taskId = t.taskId;
                atpy.taskParams = t.taskParams;
                // typeAsString will be initialized when AtlasTaskParamsYaml will be requested
                // see method ai.metaheuristic.ai.launchpad.atlas.AtlasTopLevelService.findTasks
                atpy.typeAsString = null;
                atpy.snippetExecResults = t.getExec();
                atpy.metrics = t.getMetrics();

                at.params = AtlasTaskParamsYamlUtils.BASE_YAML_UTILS.toString(atpy);
                atlasTaskRepository.save(at);
            }
        }).map(o->o.taskId).collect(Collectors.toList());

        trg.checkIntegrity();
        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public AtlasParamsYamlUtilsV2 nextUtil() {
        return atlasParamsYamlUtilsV2;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(AtlasParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public AtlasParamsYamlV1 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final AtlasParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
