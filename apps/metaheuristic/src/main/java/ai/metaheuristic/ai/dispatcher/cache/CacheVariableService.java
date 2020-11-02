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
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.File;
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
    private final VariableRepository variableRepository;

    @Transactional(readOnly = true)
    public void storeToFile(Long variableId, File trgFile) {
        try {
            Blob blob = cacheVariableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                String es = S.f("#171.220 Data for variableId #%d wasn't found", variableId);
                log.warn(es);
                throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
            }
            try (InputStream is = blob.getBinaryStream()) {
                FileUtils.copyInputStreamToFile(is, trgFile);
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Exception e) {
            String es = "#171.240 Error while storing data to file";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    public CacheVariable createInitialized(Long cacheProcessId, InputStream is, long size, String variable) {
        return createInitializedInternal(cacheProcessId, is, size, variable);
    }

    public CacheVariable createAsNull(Long cacheProcessId, String variable) {
        return createInitializedInternal(cacheProcessId, null, 0, variable);
    }

    private CacheVariable createInitializedInternal(Long cacheProcessId, @Nullable InputStream is, long size, String variable) {
        TxUtils.checkTxExists();

        CacheVariable data = new CacheVariable();
        data.cacheProcessId = cacheProcessId;
        data.variableName = variable;
        data.createdOn = System.currentTimeMillis();

        if (is==null) {
            data.setData(null);
            data.nullified = true;
        }
        else {
            data.data = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
            data.nullified = false;
        }

        data = cacheVariableRepository.save(data);

        return data;
    }
}
