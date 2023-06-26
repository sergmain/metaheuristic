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

package ai.metaheuristic.commons.yaml.function_list;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 12/12/2020
 * Time: 5:28 PM
 */
public class FunctionConfigListYamlUtilsV2
        extends AbstractParamsYamlUtils<FunctionConfigListYamlV2, FunctionConfigListYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(FunctionConfigListYamlV2.class);
    }

    @NonNull
    @Override
    public FunctionConfigListYaml upgradeTo(@NonNull FunctionConfigListYamlV2 src) {
        src.checkIntegrity();
        FunctionConfigListYaml trg = new FunctionConfigListYaml();
        trg.functions = src.functions.stream().map(fnCfgSrc-> {
            FunctionConfigListYaml.FunctionConfig fnCfgTrg = new FunctionConfigListYaml.FunctionConfig();
            BeanUtils.copyProperties(fnCfgSrc, fnCfgTrg);

            if (fnCfgSrc.checksumMap!=null) {
                fnCfgTrg.checksumMap = new HashMap<>(fnCfgSrc.checksumMap);
            }
            if (fnCfgSrc.metas!=null) {
                fnCfgTrg.metas = new ArrayList<>(fnCfgSrc.metas);
            }
            boolean paramsAsFile = MetaUtils.isTrue(fnCfgSrc.metas, ConstsApi.META_MH_FUNCTION_PARAMS_AS_FILE_META);
            if (paramsAsFile) {
                fnCfgTrg.content = fnCfgSrc.params;
                fnCfgTrg.params = null;
                final List<Map<String, String>> metas = MetaUtils.remove(fnCfgSrc.metas, ConstsApi.META_MH_FUNCTION_PARAMS_AS_FILE_META);
                if (metas!=null) {
                    fnCfgTrg.metas = metas;
                }
            }
            return  fnCfgTrg;
        }).collect(Collectors.toList());
        trg.checkIntegrity();
        return trg;
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
    public String toString(@NonNull FunctionConfigListYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public FunctionConfigListYamlV2 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final FunctionConfigListYamlV2 p = getYaml().load(yaml);
        return p;
    }

}
