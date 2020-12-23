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

package ai.metaheuristic.ai.dispatcher.replication;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public void createFunction(ReplicationData.FunctionAsset functionAsset) {
        Function sn = functionRepository.findByCode(functionAsset.function.code);
        if (sn!=null) {
            log.warn("#240,020 Function {} already registered in db", functionAsset.function.code);
            return;
        }
        //noinspection ConstantConditions
        functionAsset.function.id=null;
        //noinspection ConstantConditions
        functionAsset.function.version=null;
        functionCache.save(functionAsset.function);
    }
}