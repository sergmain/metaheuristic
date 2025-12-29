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

package ai.metaheuristic.ai.dispatcher.cache;

import ai.metaheuristic.ai.dispatcher.beans.CacheVariable;
import ai.metaheuristic.ai.dispatcher.repositories.CacheVariableRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Sergio Lissner
 * Date: 12/28/2025
 * Time: 4:25 PM
 */
@Service
@Slf4j
@Profile({"dispatcher"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class CacheBlobTxService {

    private final CacheVariableRepository cacheVariableRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CacheVariable createEmptyCacheVariable(Long cacheProcessId, String variable) {
        //TxUtils.checkTxExists();

        CacheVariable data = new CacheVariable();
        data.cacheProcessId = cacheProcessId;
        data.variableName = variable;
        data.createdOn = System.currentTimeMillis();
        data.data = null;
        data.nullified = true;

        data = cacheVariableRepository.save(data);

        return data;
    }
}
