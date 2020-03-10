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

package ai.metaheuristic.commons.yaml.event;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.api.data.event.DispatcherEventYaml;
import ai.metaheuristic.api.data.event.DispatcherEventYamlV1;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class DispatcherEventYamlUtilsV1
        extends AbstractParamsYamlUtils<DispatcherEventYamlV1, DispatcherEventYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(DispatcherEventYamlV1.class);
    }

    @NonNull
    @Override
    public DispatcherEventYaml upgradeTo(@NonNull DispatcherEventYamlV1 src, Long ... vars) {
        src.checkIntegrity();
        DispatcherEventYaml trg = new DispatcherEventYaml();
        trg.createdOn = src.createdOn;
        trg.event = src.event;
        trg.contextId = src.contextId;
        if (src.batchData!=null) {
            trg.batchData = new DispatcherEventYaml.BatchEventData();
            trg.batchData.batchId = src.batchData.batchId;
            trg.batchData.execContextId = src.batchData.execContextId;
            trg.batchData.username = src.batchData.username;
            trg.batchData.size = src.batchData.size;
            trg.batchData.filename = src.batchData.filename;
            trg.batchData.companyId = src.batchData.companyId;
        }
        if (src.taskData!=null) {
            trg.taskData = new DispatcherEventYaml.TaskEventData();
            trg.taskData.processorId = src.taskData.processorId;
            trg.taskData.taskId = src.taskData.taskId;
            trg.taskData.execContextId = src.taskData.execContextId;
        }
        trg.checkIntegrity();
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(DispatcherEventYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public DispatcherEventYamlV1 to(String s) {
        if (S.b(s)) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        final DispatcherEventYamlV1 p = getYaml().load(s);
        return p;
    }

}
