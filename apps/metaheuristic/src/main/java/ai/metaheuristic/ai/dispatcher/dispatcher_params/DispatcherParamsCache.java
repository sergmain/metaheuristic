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

package ai.metaheuristic.ai.dispatcher.dispatcher_params;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.Dispatcher;
import ai.metaheuristic.ai.dispatcher.repositories.DispatcherParamsRepository;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYamlUtils;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 4/19/2020
 * Time: 5:19 PM
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor
public class DispatcherParamsCache {

    private final DispatcherParamsRepository dispatcherParamsRepository;

    @CacheEvict(cacheNames = Consts.DISPATCHERS_CACHE, key = "#result.code")
    public Dispatcher save(Dispatcher dispatcher) {
        if (!Consts.DISPATCHERS_CACHE.equals(dispatcher.code)) {
            throw new IllegalStateException("(!Consts.DISPATCHERS_CACHE.equals(dispatcher.code))");
        }
        if (S.b(dispatcher.params)) {
            throw new IllegalStateException("(S.b(dispatcher.params))");
        }
        return dispatcherParamsRepository.save(dispatcher);
    }

    @Cacheable(cacheNames = {Consts.DISPATCHERS_CACHE}, unless="#result==null")
    public Dispatcher find() {
        Dispatcher dispatcher = dispatcherParamsRepository.findByCode(Consts.DISPATCHERS_CACHE);
        if (dispatcher==null) {
            Dispatcher entity = new Dispatcher();
            entity.code = Consts.DISPATCHERS_CACHE;
            entity.params = DispatcherParamsYamlUtils.BASE_YAML_UTILS.toString(new DispatcherParamsYaml());
            dispatcher = dispatcherParamsRepository.save(entity);
        }
        return dispatcher;
    }


}
