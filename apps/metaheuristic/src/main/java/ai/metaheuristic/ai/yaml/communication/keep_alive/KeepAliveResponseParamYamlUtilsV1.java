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

package ai.metaheuristic.ai.yaml.communication.keep_alive;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class KeepAliveResponseParamYamlUtilsV1 extends
        AbstractParamsYamlUtils<KeepAliveResponseParamYamlV1, KeepAliveResponseParamYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(KeepAliveResponseParamYamlV1.class);
    }

    @NonNull
    @Override
    public KeepAliveResponseParamYaml upgradeTo(@NonNull KeepAliveResponseParamYamlV1 v1, Long ... vars) {
        KeepAliveResponseParamYaml t = new KeepAliveResponseParamYaml();

        if( v1.dispatcherInfo !=null ) {
            t.dispatcherInfo = new KeepAliveResponseParamYaml.DispatcherInfo();
            t.dispatcherInfo.chunkSize = v1.dispatcherInfo.chunkSize;
            t.dispatcherInfo.processorCommVersion = v1.dispatcherInfo.processorCommVersion;
        }
        if (!v1.functions.infos.isEmpty()) {
            t.functions.infos.addAll( v1.functions.infos
                            .stream()
                            .map(o->new KeepAliveResponseParamYaml.Functions.Info (o.code, o.sourcing))
                            .collect(Collectors.toList())
                    );
        }
        if (v1.execContextStatus !=null) {
            t.execContextStatus = new KeepAliveResponseParamYaml.ExecContextStatus();
            t.execContextStatus.statuses =
                    v1.execContextStatus.statuses!=null
                            ? v1.execContextStatus.statuses
                            .stream()
                            .map(o->new KeepAliveResponseParamYaml.ExecContextStatus.SimpleStatus(o.execContextId, o.state))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.requestLogFile!=null) {
            t.requestLogFile = new KeepAliveResponseParamYaml.RequestLogFile(v1.requestLogFile.requestedOn);
        }
        if (v1.assignedProcessorId !=null) {
            t.assignedProcessorId = new KeepAliveResponseParamYaml.AssignedProcessorId(
                    v1.assignedProcessorId.assignedProcessorId, v1.assignedProcessorId.assignedSessionId);
        }
        if (v1.reAssignedProcessorId !=null) {
            t.reAssignedProcessorId = new KeepAliveResponseParamYaml.ReAssignedProcessorId(
                    v1.reAssignedProcessorId.reAssignedProcessorId, v1.reAssignedProcessorId.sessionId);
        }

        BeanUtils.copyProperties(v1, t);
        return t;
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
    public String toString(@NonNull KeepAliveResponseParamYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public KeepAliveResponseParamYamlV1 to(@NonNull String s) {
        final KeepAliveResponseParamYamlV1 p = getYaml().load(s);
        return p;
    }

}
