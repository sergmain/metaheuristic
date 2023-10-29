/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.*;

@SuppressWarnings("DuplicatedCode")
@Slf4j
public class FunctionCoreUtils {

    private static final FunctionApiData.FunctionConfigStatus FUNCTION_CONFIG_STATUS_OK = new FunctionApiData.FunctionConfigStatus(true, null);

    public static FunctionApiData.FunctionConfigStatus validate(FunctionConfigYaml.FunctionConfig functionConfig) {
        if ((functionConfig.file ==null || functionConfig.file.isBlank()) && (functionConfig.env ==null || functionConfig.env.isBlank())) {
            return new FunctionApiData.FunctionConfigStatus(false, "401.010 Fields 'file' and 'env' can't be null or empty both.");
        }
        if (S.b(functionConfig.code)) {
            return new FunctionApiData.FunctionConfigStatus(false, "401.015 The field 'code' is blank: " + functionConfig);
        }
        if (S.b(functionConfig.type)) {
            return new FunctionApiData.FunctionConfigStatus(false, "401.017 The field 'type is blank: " + functionConfig);
        }
        if (!StrUtils.isCodeOk(functionConfig.code)) {
            return new FunctionApiData.FunctionConfigStatus(false, "401.020 Function code has wrong chars: "+ functionConfig.code +", allowed only: " + StrUtils.ALLOWED_CHARS_IN_CODE_REGEXP);
        }
        if (functionConfig.sourcing ==null) {
            return new FunctionApiData.FunctionConfigStatus(false, "401.025 Field 'sourcing' is absent");
        }
        switch (functionConfig.sourcing) {
            case dispatcher:
                if (StringUtils.isBlank(functionConfig.file)) {
                    return new FunctionApiData.FunctionConfigStatus(false, "401.030 sourcing is 'dispatcher' but file is empty: " + functionConfig);
                }
                break;
            case processor:
                break;
            case git:
                if (functionConfig.git ==null) {
                    return new FunctionApiData.FunctionConfigStatus(false, "401.042 sourcing is 'git', but git info is absent");
                }
                break;
        }
        return FUNCTION_CONFIG_STATUS_OK;
    }

    public static String getDataForChecksumForConfigOnly(FunctionConfigYaml.FunctionConfig functionConfig) {
        return getDataForChecksumForConfigOnly(
                functionConfig.code, functionConfig.env, functionConfig.file, functionConfig.params, functionConfig.git, functionConfig.sourcing);
    }

    public static String getDataForChecksumForConfigOnly(TaskParamsYaml.FunctionConfig functionConfig) {
        return getDataForChecksumForConfigOnly(
                functionConfig.code, functionConfig.env, functionConfig.file, functionConfig.params, functionConfig.git, functionConfig.sourcing);
    }

    private static String getDataForChecksumForConfigOnly(
        String functionCode, @Nullable String env, @Nullable String functionFile,
        @Nullable String functionParams, @Nullable GitInfo git, EnumsApi.FunctionSourcing sourcing) {

        return functionCode + " " +
                (S.b(env) ? null : env) +
                ", " +
                (S.b(functionFile) ? null : functionFile) +
                " " +
                (S.b(functionParams) ? null : functionParams) +
                (git !=null ? " " + git.branch+":"+ git.commit : "") +
                (sourcing == EnumsApi.FunctionSourcing.dispatcher ? "" : " " + sourcing);
    }

    public static List<EnumsApi.OS> getSupportedOS(@Nullable List<Map<String, String>> metas) {
        final Meta meta = MetaUtils.getMeta(metas, ConstsApi.META_MH_FUNCTION_SUPPORTED_OS);
        if (meta != null && meta.value!=null && !meta.value.isBlank()) {
            try {
                StringTokenizer st = new StringTokenizer(meta.value, ", ");
                List<EnumsApi.OS> oss = new ArrayList<>();
                while (st.hasMoreTokens()) {
                    String s =  st.nextToken();
                    oss.add( EnumsApi.OS.valueOf(s) );
                }
                return oss;
            }
            catch (IllegalArgumentException e) {
                log.error("401.200 Error parsing metadata with supported OS: " + meta, e);
                return List.of();
            }
        }
        return List.of();
    }

    public static int getTaskParamsVersion(List<Map<String, String>> metas) {
        final Meta meta = MetaUtils.getMeta(metas, ConstsApi.META_MH_TASK_PARAMS_VERSION);
        return (meta!=null) ? Integer.parseInt(meta.value) : 1;
    }
}
