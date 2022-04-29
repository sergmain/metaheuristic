/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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
 * Date: 4/29/2022
 * Time: 12:53 AM
 */
public class KeepAliveRequestParamYamlUtilsV3 extends
        AbstractParamsYamlUtils<KeepAliveRequestParamYamlV3, KeepAliveRequestParamYam3, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 3;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(KeepAliveRequestParamYamlV3.class);
    }

    @NonNull
    @Override
    public KeepAliveRequestParamYaml upgradeTo(@NonNull KeepAliveRequestParamYamlV2 src) {
        KeepAliveRequestParamYamlV3 t = new KeepAliveRequestParamYamlV3();

        src.functions.statuses.stream().map(o->new KeepAliveRequestParamYamlV3.FunctionDownloadStatusesV3.StatusV3(o.code, o.state))
                .collect(Collectors.toCollection(()->t.functions.statuses));

        for (KeepAliveRequestParamYamlV2.ProcessorRequestV2 v2 : src.requests) {

            KeepAliveRequestParamYamlV3.ProcessorRequestV3 r = new KeepAliveRequestParamYamlV3.ProcessorRequestV3(v2.processorCode);
            t.requests.add(r);

            if (v2.processor !=null) {
                r.processor = new KeepAliveRequestParamYamlV3.ReportProcessorV3();
                BeanUtils.copyProperties(v2.processor, r.processor);
                if (v2.processor.env!=null) {
                    r.processor.env = new KeepAliveRequestParamYaml.Env(v2.processor.env.tags);
                    r.processor.env.mirrors.putAll(v2.processor.env.mirrors);
                    r.processor.env.envs.putAll(v2.processor.env.envs);
                    v2.processor.env.disk.stream().map(o->new KeepAliveRequestParamYaml.DiskStorage(o.code, o.path)).collect(Collectors.toCollection(() -> r.processor.env.disk));
                    v2.processor.env.quotas.values.stream().map(o->new KeepAliveRequestParamYaml.Quota(o.tag, o.amount, o.disabled)).collect(Collectors.toCollection(()->r.processor.env.quotas.values));
                    r.processor.env.quotas.limit = v2.processor.env.quotas.limit;
                    r.processor.env.quotas.disabled = v2.processor.env.quotas.disabled;
                    r.processor.env.quotas.defaultValue = v2.processor.env.quotas.defaultValue;
                }
            }
            if (v2.requestProcessorId!=null) {
                r.requestProcessorId = new KeepAliveRequestParamYamlV3.RequestProcessorIdV3();
            }
            if (v2.processorCommContext!=null) {
                r.processorCommContext = new KeepAliveRequestParamYamlV3.ProcessorCommContextV3(v2.processorCommContext.processorId, v2.processorCommContext.sessionId);
            }
            else {
                r.processorCommContext = new KeepAliveRequestParamYamlV3.ProcessorCommContextV3();
            }
        }
        return t;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public KeepAliveRequestParamYamlUtilsV3 nextUtil() {
        return (KeepAliveRequestParamYamlUtilsV3) KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.getForVersion(3);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull KeepAliveRequestParamYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public KeepAliveRequestParamYamlV2 to(@NonNull String s) {
        final KeepAliveRequestParamYamlV2 p = getYaml().load(s);
        return p;
    }

}
