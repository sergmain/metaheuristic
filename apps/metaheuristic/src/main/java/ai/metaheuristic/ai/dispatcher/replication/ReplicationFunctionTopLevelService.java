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

package ai.metaheuristic.ai.dispatcher.replication;

import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * @author Serge
 * Date: 11/24/2020
 * Time: 6:39 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ReplicationFunctionTopLevelService {

    private final ReplicationCoreService replicationCoreService;
    private final ReplicationFunctionService replicationFunctionService;
    private final FunctionRepository functionRepository;
    private final FunctionCache functionCache;
    private final FunctionService functionTopLevelService;

    public void syncFunctions(List<String> actualFunctions) {
        functionRepository.findAllFunctionCodes().stream()
                .filter(s->!actualFunctions.contains(s))
                .map(functionRepository::findByCode)
                .filter(Objects::nonNull)
                .forEach(s-> functionTopLevelService.deleteFunctionById(s.id, false));

        List<String> currFunctions = functionRepository.findAllFunctionCodes();
        actualFunctions.stream()
                .filter(s->!currFunctions.contains(s))
                .map(this::getFunctionAsset)
                .filter(Objects::nonNull)
                .forEach(replicationFunctionService::createFunction);
    }

    private ReplicationData.@Nullable FunctionAsset getFunctionAsset(String functionCode) {
        ReplicationData.FunctionAsset functionAsset = requestFunctionAsset(functionCode);
        if (functionAsset.isErrorMessages()) {
            log.error("306.020 Error while getting function {} , error: {}",  functionCode, functionAsset.getErrorMessagesAsStr());
            return null;
        }
        return functionAsset;
    }

    private ReplicationData.FunctionAsset requestFunctionAsset(String functionCode) {
        ReplicationData.ReplicationAsset data = replicationCoreService.getData(
                "/rest/v1/replication/function", ReplicationData.FunctionAsset.class, List.of(new BasicNameValuePair("functionCode", functionCode)),
                (uri) -> Request.get(uri).connectTimeout(Timeout.ofSeconds(5))
//                        .socketTimeout(20000)
        );
        if (data instanceof ReplicationData.AssetAcquiringError) {
            return new ReplicationData.FunctionAsset(((ReplicationData.AssetAcquiringError) data).getErrorMessagesAsList());
        }
        ReplicationData.FunctionAsset response = (ReplicationData.FunctionAsset) data;
        return response;
    }


}
