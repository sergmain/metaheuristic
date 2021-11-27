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

package ai.metaheuristic.ai.dispatcher.processor;

import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.commons.S;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.Objects;

/**
 * @author Serge
 * Date: 11/25/2021
 * Time: 4:30 PM
 */
public class ProcessorUtils {

    public static boolean isProcessorFunctionDownloadStatusDifferent(ProcessorStatusYaml ss, KeepAliveRequestParamYaml.FunctionDownloadStatuses status) {
        if (ss.downloadStatuses.size()!=status.statuses.size()) {
            return true;
        }
        for (ProcessorStatusYaml.DownloadStatus downloadStatus : ss.downloadStatuses) {
            for (KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status sds : status.statuses) {
                if (downloadStatus.functionCode.equals(sds.code) && !downloadStatus.functionState.equals(sds.state)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isEnvEmpty(@Nullable ProcessorStatusYaml.Env env) {
        return env==null || (CollectionUtils.isEmpty(env.envs) && CollectionUtils.isEmpty(env.mirrors) && S.b(env.tags) && CollectionUtils.isEmpty(env.quotas.values));
    }

    private static boolean isEnvEmpty(@Nullable KeepAliveRequestParamYaml.Env env) {
        return env==null || (CollectionUtils.isEmpty(env.envs) && CollectionUtils.isEmpty(env.mirrors) && S.b(env.tags));
    }

    public static boolean envNotEquals(@Nullable ProcessorStatusYaml.Env env1, @Nullable KeepAliveRequestParamYaml.Env env2) {
        final boolean envEmpty1 = isEnvEmpty(env1);
        final boolean envEmpty2 = isEnvEmpty(env2);
        if (envEmpty1 && !envEmpty2) {
            return true;
        }
        if (!envEmpty1 && envEmpty2) {
            return true;
        }

        //noinspection ConstantConditions
        if (envEmpty1 && envEmpty2) {
            return false;
        }
        if (quotasNotEquals(env1.quotas, env2.quotas)) {
            return true;
        }
        if (!CollectionUtils.isMapEquals(env1.envs, env2.envs)) {
            return true;
        }
        if (!CollectionUtils.isMapEquals(env1.mirrors, env2.mirrors)) {
            return true;

        }
        if (CollectionUtils.isNotEmpty(env1.envs) && CollectionUtils.isEmpty(env2.envs)) {
            return true;
        }
        if (env1.disk.size()!=env2.disk.size()) {
            return true;
        }
        for (ProcessorStatusYaml.DiskStorage diskStorage : env1.disk) {
            if (!env2.disk.contains(new KeepAliveRequestParamYaml.DiskStorage(diskStorage.code, diskStorage.path))) {
                return true;
            }
        }
        return StringUtils.compare(env1.tags, env2.tags)!=0;
    }

    private static boolean quotasNotEquals(ProcessorStatusYaml.Quotas quotas, KeepAliveRequestParamYaml.Quotas quotas1) {
        if (quotas.limit!=quotas1.limit) {
            return true;
        }
        if (quotas.disabled!=quotas1.disabled) {
            return true;
        }
        if (quotas.defaultValue!=quotas1.defaultValue) {
            return true;
        }
        for (ProcessorStatusYaml.Quota quota : quotas.values) {
            if (!quotas1.values.contains(new KeepAliveRequestParamYaml.Quota(quota.tag, quota.amount, quota.disabled))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isProcessorStatusDifferent(ProcessorStatusYaml ss, KeepAliveRequestParamYaml.ReportProcessor status) {

        if (envNotEquals(ss.env, status.env)) {
            return true;
        }

        return !Objects.equals(ss.gitStatusInfo, status.gitStatusInfo) ||
                !Objects.equals(ss.schedule, status.schedule) ||
                !Objects.equals(ss.ip, status.ip) ||
                !Objects.equals(ss.host, status.host) ||
                !CollectionUtils.isEquals(ss.errors, status.errors) ||
                ss.logDownloadable!=status.logDownloadable ||
                ss.taskParamsVersion!=status.taskParamsVersion||
                ss.os!=status.os ||
                !Objects.equals(ss.currDir, status.currDir);
    }
}
