/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class KeepAliveResponseParamYamlUtilsV2 extends
        AbstractParamsYamlUtils<KeepAliveResponseParamYamlV2, KeepAliveResponseParamYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(KeepAliveResponseParamYamlV2.class);
    }

    @NonNull
    @Override
    public KeepAliveResponseParamYaml upgradeTo(@NonNull KeepAliveResponseParamYamlV2 v2) {
        KeepAliveResponseParamYaml t = new KeepAliveResponseParamYaml();

        if (v2.dispatcherInfo !=null) {
            t.dispatcherInfo = new KeepAliveResponseParamYaml.DispatcherInfo();
            t.dispatcherInfo.chunkSize = v2.dispatcherInfo.chunkSize;
            t.dispatcherInfo.processorCommVersion = v2.dispatcherInfo.processorCommVersion;
        }
        t.execContextStatus.statuses.putAll(v2.execContextStatus.statuses);

        KeepAliveResponseParamYaml.DispatcherResponse response = t.response;
        KeepAliveResponseParamYamlV2.DispatcherResponseV2 r = v2.response;
        response.processorCode = r.processorCode;

        if (r.assignedProcessorId !=null) {
            response.assignedProcessorId = new KeepAliveResponseParamYaml.AssignedProcessorId(
                    r.assignedProcessorId.assignedProcessorId, r.assignedProcessorId.assignedSessionId);
        }
        if (r.reAssignedProcessorId !=null) {
            response.reAssignedProcessorId = new KeepAliveResponseParamYaml.ReAssignedProcessorId(
                    r.reAssignedProcessorId.reAssignedProcessorId, r.reAssignedProcessorId.sessionId);
        }

        if (r.requestLogFile!=null) {
            response.requestLogFile = new KeepAliveResponseParamYaml.RequestLogFile(r.requestLogFile.requestedOn);
        }

        for (KeepAliveResponseParamYamlV2.CoreInfoV2 coreInfoV2 : r.coreInfos) {
            response.coreInfos.add(new KeepAliveResponseParamYaml.CoreInfo(coreInfoV2.coreId, coreInfoV2.code));
        }

        t.success = v2.success;
        t.msg = v2.msg;

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
    public String toString(@NonNull KeepAliveResponseParamYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public KeepAliveResponseParamYamlV2 to(@NonNull String s) {
        final KeepAliveResponseParamYamlV2 p = getYaml().load(s);
        return p;
    }

}
