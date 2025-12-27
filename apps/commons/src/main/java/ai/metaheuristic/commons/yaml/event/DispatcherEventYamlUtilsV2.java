/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.api.data.event.DispatcherEventYaml;
import ai.metaheuristic.api.data.event.DispatcherEventYamlV2;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import javax.annotation.Nonnull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class DispatcherEventYamlUtilsV2
        extends AbstractParamsYamlUtils<DispatcherEventYamlV2, DispatcherEventYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @Nonnull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(DispatcherEventYamlV2.class);
    }

    @Nonnull
    @Override
    public DispatcherEventYaml upgradeTo(@Nonnull DispatcherEventYamlV2 src) {
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
            trg.taskData.coreId = src.taskData.coreId;
            trg.taskData.taskId = src.taskData.taskId;
            trg.taskData.execContextId = src.taskData.execContextId;
            trg.taskData.context = src.taskData.context;
            trg.taskData.funcCode = src.taskData.funcCode;
        }
        trg.checkIntegrity();
        return trg;
    }

    @Nonnull
    @Override
    public Void downgradeTo(@Nonnull Void yaml) {
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
    public String toString(@Nonnull DispatcherEventYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @Nonnull
    @Override
    public DispatcherEventYamlV2 to(@Nonnull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        //noinspection UnnecessaryLocalVariable
        final DispatcherEventYamlV2 p = getYaml().load(yaml);
        return p;
    }

}
