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

package ai.metaheuristic.ai.yaml.communication.keep_alive;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class KeepAliveResponseParamYamlUtilsV1 extends
        AbstractParamsYamlUtils<KeepAliveResponseParamYamlV1, KeepAliveResponseParamYamlV2, KeepAliveResponseParamYamlUtilsV2, Void, Void, Void> {

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
    public KeepAliveResponseParamYamlV2 upgradeTo(@NonNull KeepAliveResponseParamYamlV1 v1) {
        KeepAliveResponseParamYamlV2 t = new KeepAliveResponseParamYamlV2();

        if( v1.dispatcherInfo !=null ) {
            t.dispatcherInfo = new KeepAliveResponseParamYamlV2.DispatcherInfoV2();
            t.dispatcherInfo.chunkSize = v1.dispatcherInfo.chunkSize;
            t.dispatcherInfo.processorCommVersion = v1.dispatcherInfo.processorCommVersion;
        }
        if (!v1.functions.infos.isEmpty()) {
            t.functions.infos.addAll( v1.functions.infos
                            .stream()
                            .map(o->new KeepAliveResponseParamYamlV2.FunctionsV2.Info (o.code, o.sourcing))
                            .collect(Collectors.toList())
                    );
        }
        if (v1.execContextStatus !=null) {
            t.execContextStatus = new KeepAliveResponseParamYamlV2.ExecContextStatusV2();
            v1.execContextStatus.statuses
                    .stream()
                    .map(o -> new KeepAliveResponseParamYamlV2.ExecContextStatusV2.SimpleStatus(o.id, o.state))
                    .collect(Collectors.toCollection(()->t.execContextStatus.statuses));
        }
        for (KeepAliveResponseParamYamlV1.DispatcherResponseV1 r : v1.responses) {

            KeepAliveResponseParamYamlV2.DispatcherResponseV2 response = new KeepAliveResponseParamYamlV2.DispatcherResponseV2();
            t.responses.add(response);

            response.processorCode = r.processorCode;

            if (r.assignedProcessorId !=null) {
                response.assignedProcessorId = new KeepAliveResponseParamYamlV2.AssignedProcessorIdV2(
                        r.assignedProcessorId.assignedProcessorId, r.assignedProcessorId.assignedSessionId);
            }
            if (r.reAssignedProcessorId !=null) {
                response.reAssignedProcessorId = new KeepAliveResponseParamYamlV2.ReAssignedProcessorIdV2(
                        r.reAssignedProcessorId.reAssignedProcessorId, r.reAssignedProcessorId.sessionId);
            }

            if (r.requestLogFile!=null) {
                response.requestLogFile = new KeepAliveResponseParamYamlV2.RequestLogFileV2(r.requestLogFile.requestedOn);
            }
        }

        t.success = v1.success;
        t.msg = v1.msg;

        return t;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public KeepAliveResponseParamYamlUtilsV2 nextUtil() {
        return (KeepAliveResponseParamYamlUtilsV2) KeepAliveResponseParamYamlUtils.BASE_YAML_UTILS.getForVersion(2);
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
