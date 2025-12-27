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
package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.storage.DispatcherBlobStorage;
import ai.metaheuristic.ai.dispatcher.storage.GeneralBlobTxService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FunctionTxService {

    private final FunctionCache functionCache;
    private final FunctionRepository functionRepository;
    private final FunctionDataTxService functionDataService;
    private final GeneralBlobTxService generalBlobTxService;
    private final DispatcherBlobStorage dispatcherBlobStorage;

    @Transactional
    public void deleteFunction(Long functionId, String functionCode) {
        functionCache.delete(functionId);
        functionDataService.deleteByFunctionCode(functionCode);
    }

    @Transactional
    public Function persistFunction(FunctionConfigYaml functionConfig, @Nullable InputStream inputStream, long size) {
        Function function = new Function();
        function.code = functionConfig.function.code;
        function.type = functionConfig.function.type!=null ? functionConfig.function.type : "";
        function.updateParams(functionConfig);

        String functionCode = function.getCode();
        function = functionCache.save(function);
        if (inputStream!=null) {
            Long functionDataId = generalBlobTxService.createEmptyFunctionData(functionCode);
            dispatcherBlobStorage.storeFunctionData(functionDataId, inputStream, size);
        }
        return function;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Pair<EnumsApi.FunctionSourcing, String>> collectInfoAboutFunction1() {
        return functionRepository.findAllAsStream()
                .map(function->{
                    FunctionConfigYaml fcy = function.getFunctionConfigYaml();
                    return Pair.of(fcy.function.sourcing, function.code);
                })
                .collect(Collectors.toList());
    }

}
