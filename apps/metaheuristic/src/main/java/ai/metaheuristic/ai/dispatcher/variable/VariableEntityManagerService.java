/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Timestamp;

/**
 * @author Sergio Lissner
 * Date: 5/26/2023
 * Time: 3:05 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class VariableEntityManagerService {

    private final EntityManager em;
    private final VariableRepository variableRepository;

    public Variable createInitialized(InputStream is, long size, String variable, @Nullable String filename, Long execContextId, String taskContextId) {
        if (size==0) {
            throw new IllegalStateException("#171.600 Variable can't be of zero length");
        }
        TxUtils.checkTxExists();

        Variable data = new Variable();
        data.inited = true;
        data.nullified = false;
        data.setName(variable);
        data.setFilename(filename);
        data.setExecContextId(execContextId);
        data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(EnumsApi.DataSourcing.dispatcher, variable)));
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));
        data.setTaskContextId(taskContextId);

        Blob blob = Hibernate.getLobCreator(em.unwrap(SessionImplementor.class)).createBlob(is, size);
        data.setData(blob);

        variableRepository.save(data);

        return data;
    }

    public void update(InputStream is, long size, Variable data) {
        TxUtils.checkTxExists();
        VariableSyncService.checkWriteLockPresent(data.id);

        if (size==0) {
            throw new IllegalStateException("#171.690 Variable can't be with zero length");
        }
        TxUtils.checkTxExists();
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(SessionImplementor.class)).createBlob(is, size);
        data.setData(blob);
        data.inited = true;
        data.nullified = false;
        variableRepository.save(data);
    }

    @Transactional
    public void storeData(InputStream is, long size, Long variableId, @Nullable String filename) {
        VariableSyncService.checkWriteLockPresent(variableId);

        if (size==0) {
            throw new IllegalStateException("#171.720 Variable can't be with zero length");
        }
        TxUtils.checkTxExists();

        Variable data = variableRepository.findById(variableId).orElse(null);
        if (data==null) {
            log.error("#171.750 can't find variable #" + variableId);
            return;
        }
        data.filename = filename;
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(SessionImplementor.class)).createBlob(is, size);
        data.setData(blob);
        data.inited = true;
        data.nullified = false;

        variableRepository.save(data);
    }
}
