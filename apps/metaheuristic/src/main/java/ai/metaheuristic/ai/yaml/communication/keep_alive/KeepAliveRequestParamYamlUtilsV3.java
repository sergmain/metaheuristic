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

package ai.metaheuristic.ai.yaml.communication.keep_alive;

import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeanUtils;

import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 4/29/2022
 * Time: 12:53 AM
 */
public class KeepAliveRequestParamYamlUtilsV3 extends
        AbstractParamsYamlUtils<KeepAliveRequestParamYamlV3, KeepAliveRequestParamYaml, Void, Void, Void, Void> {

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
    public KeepAliveRequestParamYaml upgradeTo(@NonNull KeepAliveRequestParamYamlV3 src) {
        KeepAliveRequestParamYaml t = new KeepAliveRequestParamYaml();

/*
        t.functions.statuses.putAll(src.functions.statuses);
*/

        BeanUtils.copyProperties(src.processor, t.processor);
        if (src.processor.status!=null) {
            t.processor.status = new KeepAliveRequestParamYaml.ProcessorStatus();

            if (src.processor.status.env != null) {
                t.processor.status.env = new KeepAliveRequestParamYaml.Env();
                t.processor.status.env.mirrors.putAll(src.processor.status.env.mirrors);
                t.processor.status.env.envs.putAll(src.processor.status.env.envs);
                src.processor.status.env.disk.stream().map(o -> new KeepAliveRequestParamYaml.DiskStorage(o.code, o.path)).collect(Collectors.toCollection(() -> t.processor.status.env.disk));
                setQuotas(src.processor.status.env.quotas, t.processor.status.env.quotas);
                if (src.processor.status.errors != null) {
                    t.processor.status.errors = src.processor.status.errors;
                }
                if (src.processor.status.gitStatusInfo!=null) {
                    t.processor.status.gitStatusInfo = new GtiUtils.GitStatusInfo(
                            src.processor.status.gitStatusInfo.status, src.processor.status.gitStatusInfo.version, src.processor.status.gitStatusInfo.error); ;
                }

                t.processor.status.schedule = src.processor.status.schedule;
                t.processor.status.ip = src.processor.status.ip;
                t.processor.status.host = src.processor.status.host;
                t.processor.status.logDownloadable = src.processor.status.logDownloadable;
                t.processor.status.taskParamsVersion = src.processor.status.taskParamsVersion;
                t.processor.status.os = src.processor.status.os;
                t.processor.status.currDir = src.processor.status.currDir;
            }
        }
        if (src.processor.processorCommContext != null) {
            t.processor.processorCommContext = new KeepAliveRequestParamYaml.ProcessorCommContext(
                    src.processor.processorCommContext.processorId, src.processor.processorCommContext.sessionId);
        }
        for (KeepAliveRequestParamYamlV3.CoreV3 V3 : src.cores) {
            KeepAliveRequestParamYaml.Core r = new KeepAliveRequestParamYaml.Core(V3.coreDir, V3.coreId, V3.coreCode, V3.tags);
            t.cores.add(r);
        }
        return t;
    }

    private static void setQuotas(KeepAliveRequestParamYamlV3.QuotasV3 quotas, KeepAliveRequestParamYaml.Quotas q) {
        q.limit = quotas.limit;
        q.defaultValue = quotas.defaultValue;
        q.disabled = quotas.disabled;
        quotas.values.stream().map(o->new KeepAliveRequestParamYaml.Quota(o.tag, o.amount, o.disabled)).collect(Collectors.toCollection(() -> q.values));
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
    public String toString(@NonNull KeepAliveRequestParamYamlV3 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public KeepAliveRequestParamYamlV3 to(@NonNull String s) {
        final KeepAliveRequestParamYamlV3 p = getYaml().load(s);
        return p;
    }

}
