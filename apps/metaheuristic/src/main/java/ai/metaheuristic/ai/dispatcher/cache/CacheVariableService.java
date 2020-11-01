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

package ai.metaheuristic.ai.dispatcher.cache;

import ai.metaheuristic.ai.dispatcher.beans.CacheVariable;
import ai.metaheuristic.ai.dispatcher.repositories.CacheVariableRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import java.io.InputStream;
import java.sql.Blob;

/**
 * @author Serge
 * Date: 11/1/2020
 * Time: 3:41 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class CacheVariableService {

    private final EntityManager em;
    private final CacheVariableRepository cacheVariableRepository;

    public CacheVariable createInitialized(Long cacheProcessId, InputStream is, long size, String variable) {
        TxUtils.checkTxExists();

        CacheVariable data = new CacheVariable();
        data.cacheProcessId = cacheProcessId;
        data.variableName = variable;
        data.createdOn = System.currentTimeMillis();

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);

        data = cacheVariableRepository.save(data);

        return data;
    }

}
