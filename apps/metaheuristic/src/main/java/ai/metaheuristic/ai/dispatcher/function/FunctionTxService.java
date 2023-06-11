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
package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class FunctionTxService {

    private final FunctionCache functionCache;
    private final FunctionRepository functionRepository;
    private final FunctionDataService functionDataService;
    private final FunctionDataWithEntityManagerService functionDataWithEntityManagerService;

    @Transactional
    public void deleteFunction(Long functionId, String functionCode) {
        functionCache.delete(functionId);
        functionDataService.deleteByFunctionCode(functionCode);
    }

    @Transactional
    public Function persistFunction(FunctionConfigYaml functionConfig, @Nullable InputStream inputStream, long size) {
        Function function = new Function();
        function.code = functionConfig.code;
        function.type = functionConfig.type!=null ? functionConfig.type : "";
        function.updateParams(functionConfig);

        String functionCode = function.getCode();
        function = functionCache.save(function);
        if (inputStream!=null) {
            functionDataWithEntityManagerService.save(inputStream, size, functionCode);
        }
        return function;
    }

    public List<Pair<EnumsApi.FunctionSourcing, String>> collectInfoAboutFunction() {
        final List<Long> allIds = functionRepository.findAllIds();

        final List<Pair<EnumsApi.FunctionSourcing, String>> result = allIds.stream()
                .map(id -> functionRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(s -> {
                    FunctionConfigYaml fcy = s.getFunctionConfigYaml();
                    return Pair.of(fcy.sourcing, s.code);
                })
                .collect(Collectors.toList());
        return result;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Pair<EnumsApi.FunctionSourcing, String>> collectInfoAboutFunction1() {
        return functionRepository.findAllAsStream()
                .map(function->{
                    FunctionConfigYaml fcy = function.getFunctionConfigYaml();
                    return Pair.of(fcy.sourcing, function.code);
                })
                .collect(Collectors.toList());
    }

}