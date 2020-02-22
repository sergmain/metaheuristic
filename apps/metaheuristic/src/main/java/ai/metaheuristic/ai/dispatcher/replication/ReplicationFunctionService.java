/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * @author Serge
 * Date: 1/13/2020
 * Time: 7:10 PM
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("dispatcher")
public class ReplicationFunctionService {

    public final ReplicationCoreService replicationCoreService;
    public final FunctionRepository functionRepository;
    public final FunctionCache functionCache;

    public void syncFunctions(List<String> actualFunctions) {
        functionRepository.findAllFunctionCodes().parallelStream()
                .filter(s->!actualFunctions.contains(s))
                .map(functionRepository::findByCode)
                .filter(Objects::nonNull)
                .forEach(s-> functionRepository.deleteById(s.id));

        List<String> currFunctions = functionRepository.findAllFunctionCodes();
        actualFunctions.parallelStream()
                .filter(s->!currFunctions.contains(s))
                .forEach(this::createFunction);
    }

    private void createFunction(String functionCode) {
        ReplicationData.FunctionAsset functionAsset = requestFunctionAsset(functionCode);
        if (functionAsset.isErrorMessages()) {
            log.error("#308.010 Error while getting function "+ functionCode +", error: " + functionAsset.getErrorMessagesAsStr());
            return;
        }
        Function sn = functionRepository.findByCode(functionCode);
        if (sn!=null) {
            return;
        }
        functionAsset.function.id=null;
        functionCache.save(functionAsset.function);
    }

    public ReplicationData.FunctionAsset requestFunctionAsset(String functionCode) {
        ReplicationData.ReplicationAsset data = replicationCoreService.getData(
                "/rest/v1/replication/function", ReplicationData.FunctionAsset.class,
                (uri) -> Request.Post(uri)
                        .bodyForm(Form.form().add("functionCode", functionCode).build(), StandardCharsets.UTF_8)
                        .connectTimeout(5000)
                        .socketTimeout(20000)
        );
        if (data instanceof ReplicationData.AssetAcquiringError) {
            return new ReplicationData.FunctionAsset(((ReplicationData.AssetAcquiringError) data).errorMessages);
        }
        ReplicationData.FunctionAsset response = (ReplicationData.FunctionAsset) data;
        return response;
    }

}