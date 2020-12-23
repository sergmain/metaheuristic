/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class FunctionService {

    private final FunctionRepository functionRepository;
    private final FunctionCache functionCache;
    private final FunctionDataService functionDataService;

    public static void sortExperimentFunctions(List<Function> functions) {
        functions.sort(FunctionService::experimentFunctionComparator);
    }

    private static int experimentFunctionComparator(Function o1, Function o2) {
        if (o1.getType().equals(o2.getType())) {
            return 0;
        }
        switch (o1.getType().toLowerCase()) {
            case CommonConsts.FIT_TYPE:
                return -1;
            case CommonConsts.PREDICT_TYPE:
                return CommonConsts.FIT_TYPE.equals(o2.getType().toLowerCase()) ? 1 : -1;
            case CommonConsts.CHECK_FITTING_TYPE:
                return 1;
            default:
                return 0;
        }
    }

    private static final long FUNCTION_INFOS_TIMEOUT_REFRESH = TimeUnit.SECONDS.toMillis(30);
    private List<KeepAliveResponseParamYaml.Functions.Info> functionInfosCache = new ArrayList<>();
    private long mills = 0L;

    public synchronized List<KeepAliveResponseParamYaml.Functions.Info> getFunctionInfos() {
        if (System.currentTimeMillis() - mills > FUNCTION_INFOS_TIMEOUT_REFRESH) {
            mills = System.currentTimeMillis();
            final List<Long> allIds = functionRepository.findAllIds();
            functionInfosCache = allIds.stream()
                    .map(functionCache::findById)
                    .filter(Objects::nonNull)
                    .map(s->{
                        FunctionConfigYaml fcy = FunctionConfigYamlUtils.BASE_YAML_UTILS.to(s.params);
                        return new KeepAliveResponseParamYaml.Functions.Info(s.code, fcy.sourcing);
                    })
                    .collect(Collectors.toList());
        }
        return functionInfosCache;
    }

    @Transactional
    public Void deleteFunction(Long functionId, String functionCode) {
        functionCache.delete(functionId);
        functionDataService.deleteByFunctionCode(functionCode);
        return null;
    }

    @Transactional
    public Function persistFunction(FunctionConfigYaml functionConfig, @Nullable InputStream inputStream, long size) {
        Function function = new Function();
        function.code = functionConfig.code;
        function.type = functionConfig.type;
        function.params = FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(functionConfig);

        String functionCode = function.getCode();
        function = functionCache.save(function);
        if (inputStream!=null) {
            functionDataService.save(inputStream, size, functionCode);
        }
        return function;
    }

}
