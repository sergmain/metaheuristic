/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function_list.FunctionConfigListYaml;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

@Slf4j
public class FunctionCoreUtils {

    private static final FunctionApiData.FunctionConfigStatus FUNCTION_CONFIG_STATUS_OK = new FunctionApiData.FunctionConfigStatus(true, null);

    public static FunctionConfigYaml to(FunctionConfigListYaml.FunctionConfig snSrc) {
        FunctionConfigYaml snTrg = new FunctionConfigYaml();
        BeanUtils.copyProperties(snSrc, snTrg);

        if (snSrc.checksumMap != null) {
            snTrg.checksumMap = new HashMap<>(snSrc.checksumMap);
        }
        if (snSrc.info != null) {
            snTrg.info = new FunctionConfigYaml.FunctionInfo(snSrc.info.signed, snSrc.info.length);
        }
        if (snSrc.metas != null) {
            snTrg.metas = new ArrayList<>(snSrc.metas);
        }
        if (snSrc.ml!=null) {
            snTrg.ml = new FunctionConfigYaml.MachineLearning(true, false);
        }
        return  snTrg;

    }

    public static FunctionConfigYaml to1(FunctionConfigListYaml.FunctionConfig snSrc) {
        FunctionConfigYaml snTrg = new FunctionConfigYaml();
        BeanUtils.copyProperties(snSrc, snTrg);

        if (snSrc.checksumMap != null) {
            snTrg.checksumMap = new HashMap<>(snSrc.checksumMap);
        }
        if (snSrc.info != null) {
            snTrg.info = new FunctionConfigYaml.FunctionInfo(snSrc.info.signed, snSrc.info.length);
        }
        if (snSrc.metas != null) {
            snTrg.metas = new ArrayList<>(snSrc.metas);
        }
        if (snSrc.ml!=null) {
            snTrg.ml = new FunctionConfigYaml.MachineLearning(true, false);
        }
        return  snTrg;

    }

    public static FunctionApiData.FunctionConfigStatus validate(FunctionConfigListYaml.FunctionConfig functionConfig) {
        if ((functionConfig.file ==null || functionConfig.file.isBlank()) && (functionConfig.env ==null || functionConfig.env.isBlank())) {
            return new FunctionApiData.FunctionConfigStatus(false, "#401.10 Fields 'file' and 'env' can't be null or empty both.");
        }
        if (functionConfig.code ==null || functionConfig.code.isBlank() || functionConfig.type ==null || functionConfig.type.isBlank()) {
            return new FunctionApiData.FunctionConfigStatus(false, "#401.15 A field is null or empty: " + functionConfig.toString());
        }
        if (!StrUtils.isCodeOk(functionConfig.code)) {
            return new FunctionApiData.FunctionConfigStatus(false, "#401.20 Function code has wrong chars: "+ functionConfig.code +", allowed only: " + StrUtils.ALLOWED_CHARS_IN_CODE_REGEXP);
        }
        if (functionConfig.sourcing ==null) {
            return new FunctionApiData.FunctionConfigStatus(false, "#401.25 Field 'sourcing' is absent");
        }
        switch (functionConfig.sourcing) {
            case dispatcher:
                if (StringUtils.isBlank(functionConfig.file)) {
                    return new FunctionApiData.FunctionConfigStatus(false, "#401.30 sourcing is 'dispatcher' but file is empty: " + functionConfig.toString());
                }
                break;
            case station:
                break;
            case git:
                if (functionConfig.git ==null) {
                    return new FunctionApiData.FunctionConfigStatus(false, "#401.42 sourcing is 'git', but git info is absent");
                }
                break;
        }
        return FUNCTION_CONFIG_STATUS_OK;
    }

    public static String getDataForChecksumWhenGitSourcing(FunctionConfigListYaml.FunctionConfig functionConfig) {
        return "" + functionConfig.env+", " + functionConfig.file +" " + functionConfig.params;
    }


    public static List<EnumsApi.OS> getSupportedOS(List<Meta> metas) {
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
                log.error("#311.001 Error parsing metadata with supported OS: " + meta, e);
                return List.of();
            }
        }
        return List.of();
    }

    // TODO 2020-02-14 why it was deprecated?
    @SuppressWarnings("deprecation")
    public static int getTaskParamsVersion(List<Meta> metas) {
        final Meta meta = MetaUtils.getMeta(metas, ConstsApi.META_MH_TASK_PARAMS_VERSION);
        return (meta!=null) ? Integer.parseInt(meta.value) : 1;
    }
}
