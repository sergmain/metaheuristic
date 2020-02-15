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
import ai.metaheuristic.api.data.event.LaunchpadEventYaml;
import ai.metaheuristic.api.data.event.LaunchpadEventYamlV1;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class LaunchpadEventYamlUtilsV1
        extends AbstractParamsYamlUtils<LaunchpadEventYamlV1, LaunchpadEventYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(LaunchpadEventYamlV1.class);
    }

    @Override
    public LaunchpadEventYaml upgradeTo(LaunchpadEventYamlV1 src, Long ... vars) {
        src.checkIntegrity();
        LaunchpadEventYaml trg = new LaunchpadEventYaml();
        trg.createdOn = src.createdOn;
        trg.event = src.event;
        trg.contextId = src.contextId;
        if (src.batchData!=null) {
            trg.batchData = new LaunchpadEventYaml.BatchEventData();
            trg.batchData.batchId = src.batchData.batchId;
            trg.batchData.execContextId = src.batchData.execContextId;
            trg.batchData.username = src.batchData.username;
            trg.batchData.size = src.batchData.size;
            trg.batchData.filename = src.batchData.filename;
            trg.batchData.companyId = src.batchData.companyId;
        }
        if (src.taskData!=null) {
            trg.taskData = new LaunchpadEventYaml.TaskEventData();
            trg.taskData.stationId = src.taskData.stationId;
            trg.taskData.taskId = src.taskData.taskId;
            trg.taskData.execContextId = src.taskData.execContextId;
        }
        trg.checkIntegrity();
        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
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
    public String toString(LaunchpadEventYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public LaunchpadEventYamlV1 to(String s) {
        if (S.b(s)) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        final LaunchpadEventYamlV1 p = getYaml().load(s);
        return p;
    }

}
