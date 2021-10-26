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
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class KeepAliveRequestParamYamlUtilsV1 extends
        AbstractParamsYamlUtils<KeepAliveRequestParamYamlV1, KeepAliveRequestParamYamlV2, KeepAliveRequestParamYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(KeepAliveRequestParamYamlV1.class);
    }

    @NonNull
    @Override
    public KeepAliveRequestParamYamlV2 upgradeTo(@NonNull KeepAliveRequestParamYamlV1 src) {
        KeepAliveRequestParamYamlV2 t = new KeepAliveRequestParamYamlV2();

        src.functions.statuses.stream().map(o->new KeepAliveRequestParamYamlV2.FunctionDownloadStatusesV2.Status(o.code, o.state))
                .collect(Collectors.toCollection(()->t.functions.statuses));

        for (KeepAliveRequestParamYamlV1.ProcessorRequestV1 v1 : src.requests) {

            KeepAliveRequestParamYamlV2.ProcessorRequestV2 r = new KeepAliveRequestParamYamlV2.ProcessorRequestV2(v1.processorCode);
            t.requests.add(r);

            if (v1.processor !=null) {
                r.processor = new KeepAliveRequestParamYamlV2.ReportProcessorV2();
                BeanUtils.copyProperties(v1.processor, r.processor);
                if (v1.processor.env!=null) {
                    r.processor.env = new KeepAliveRequestParamYamlV2.EnvV2(v1.processor.env.tags);
                    r.processor.env.mirrors.putAll(v1.processor.env.mirrors);
                    r.processor.env.envs.putAll(v1.processor.env.envs);
                    r.processor.env.quotas.disabled = true;
                    v1.processor.env.disk.stream().map(o->new KeepAliveRequestParamYamlV2.DiskStorageV2(o.code, o.path)).collect(Collectors.toCollection(() -> r.processor.env.disk));
                }
            }
            if (v1.requestProcessorId!=null) {
                r.requestProcessorId = new KeepAliveRequestParamYamlV2.RequestProcessorIdV2();
            }
            if (v1.processorCommContext!=null) {
                r.processorCommContext = new KeepAliveRequestParamYamlV2.ProcessorCommContextV2(v1.processorCommContext.processorId, v1.processorCommContext.sessionId);
            }
            else {
                r.processorCommContext = new KeepAliveRequestParamYamlV2.ProcessorCommContextV2();
            }
            r.taskIds = v1.taskIds;
        }
        return t;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public KeepAliveRequestParamYamlUtilsV2 nextUtil() {
        return (KeepAliveRequestParamYamlUtilsV2) KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull KeepAliveRequestParamYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public KeepAliveRequestParamYamlV1 to(@NonNull String s) {
        final KeepAliveRequestParamYamlV1 p = getYaml().load(s);
        return p;
    }

}
