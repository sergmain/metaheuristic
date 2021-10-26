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
public class KeepAliveRequestParamYamlUtilsV2 extends
        AbstractParamsYamlUtils<KeepAliveRequestParamYamlV2, KeepAliveRequestParamYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(KeepAliveRequestParamYamlV2.class);
    }

    @NonNull
    @Override
    public KeepAliveRequestParamYaml upgradeTo(@NonNull KeepAliveRequestParamYamlV2 src) {
        KeepAliveRequestParamYaml t = new KeepAliveRequestParamYaml();

        src.functions.statuses.stream().map(o->new KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status(o.code, o.state))
                .collect(Collectors.toCollection(()->t.functions.statuses));

        for (KeepAliveRequestParamYamlV2.ProcessorRequestV2 v2 : src.requests) {

            KeepAliveRequestParamYaml.ProcessorRequest r = new KeepAliveRequestParamYaml.ProcessorRequest(v2.processorCode);
            t.requests.add(r);

            if (v2.processor !=null) {
                r.processor = new KeepAliveRequestParamYaml.ReportProcessor();
                BeanUtils.copyProperties(v2.processor, r.processor);
                if (v2.processor.env!=null) {
                    r.processor.env = new KeepAliveRequestParamYaml.Env(v2.processor.env.tags);
                    r.processor.env.mirrors.putAll(v2.processor.env.mirrors);
                    r.processor.env.envs.putAll(v2.processor.env.envs);
                    v2.processor.env.disk.stream().map(o->new KeepAliveRequestParamYaml.DiskStorage(o.code, o.path)).collect(Collectors.toCollection(() -> r.processor.env.disk));
                    v2.processor.env.quotas.values.stream().map(o->new KeepAliveRequestParamYaml.Quota(o.tag, o.amount)).collect(Collectors.toCollection(()->r.processor.env.quotas.values));
                    r.processor.env.quotas.limit = v2.processor.env.quotas.limit;
                    r.processor.env.quotas.disabled = v2.processor.env.quotas.disabled;
                    r.processor.env.quotas.defaultValue = v2.processor.env.quotas.defaultValue;
                }
            }
            if (v2.requestProcessorId!=null) {
                r.requestProcessorId = new KeepAliveRequestParamYaml.RequestProcessorId();
            }
            if (v2.processorCommContext!=null) {
                r.processorCommContext = new KeepAliveRequestParamYaml.ProcessorCommContext(v2.processorCommContext.processorId, v2.processorCommContext.sessionId);
            }
            else {
                r.processorCommContext = new KeepAliveRequestParamYaml.ProcessorCommContext();
            }
            r.taskIds = v2.taskIds;
        }
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
