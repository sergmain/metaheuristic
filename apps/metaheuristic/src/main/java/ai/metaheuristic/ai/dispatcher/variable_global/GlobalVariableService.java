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

package ai.metaheuristic.ai.dispatcher.variable_global;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.File;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

import static ai.metaheuristic.api.EnumsApi.DataSourcing;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class GlobalVariableService {

    private final EntityManager em;
    private final GlobalVariableRepository globalVariableRepository;
    private final Globals globals;

    @Nullable
    @Transactional(readOnly = true)
    public GlobalVariable getBinaryData(Long id) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("#089.010 this method intended to be only for test cases");
        }
        return getBinaryData(id, true);
    }

    @Nullable
    private GlobalVariable getBinaryData(Long id, @SuppressWarnings("SameParameterValue") boolean isInitBytes) {
        try {
            GlobalVariable data = globalVariableRepository.findById(id).orElse(null);
            if (data==null) {
                return null;
            }
            if (isInitBytes) {
                data.bytes = data.getData().getBytes(1, (int) data.getData().length());
            }
            return data;
        } catch (SQLException e) {
            throw new IllegalStateException("#089.020 SQL error", e);
        }
    }

    @Transactional(readOnly = true)
    public void storeToFile(Long variableId, File trgFile) {
        try {
            Blob blob = globalVariableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                log.warn("#089.030 Binary data for variableId {} wasn't found", variableId);
                throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.global, "#089.040 Binary data wasn't found, variableId: " + variableId);
            }
            try (InputStream is = blob.getBinaryStream()) {
                FileUtils.copyInputStreamToFile(is, trgFile);
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Exception e) {
            String es = "#089.050 Error while storing binary data";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    @Transactional
    public void deleteByVariable(String variable) {
        globalVariableRepository.deleteByName(variable);
    }

    @Transactional
    public GlobalVariable save(InputStream is, long size, String variable, String filename) {
        GlobalVariable data = new GlobalVariable();
        data.setName(variable);
        data.setFilename(filename);
        data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.dispatcher, variable)));
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);

        globalVariableRepository.save(data);

        return data;
    }

    @SuppressWarnings("UnusedReturnValue")
    @Transactional
    public GlobalVariable createGlobalVariableWithExternalStorage(String variable, String params) {

        GlobalVariable data = new GlobalVariable();
        data.setName(variable);
        data.setFilename(null);
        data.setParams(params);
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));
        data.setData(null);
        globalVariableRepository.save(data);

        return data;
    }

    @Transactional
    public void update(InputStream is, long size, GlobalVariable data) {
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);

        globalVariableRepository.save(data);
    }

    public Page<GlobalVariable> findAll(Pageable pageable) {
        return globalVariableRepository.findAll(pageable);
    }

    public void deleteById(Long id) {
        globalVariableRepository.deleteById(id);
    }

    public Optional<GlobalVariable> findById(Long id) {
        return globalVariableRepository.findById(id);
    }

    public Slice<SimpleGlobalVariable> getAllAsSimpleGlobalVariable(Pageable pageable) {
        return globalVariableRepository.getAllAsSimpleGlobalVariable(pageable);
    }

    @Nullable
    public SimpleGlobalVariable getByIdAsSimpleGlobalVariable(Long id) {
        return globalVariableRepository.getByIdAsSimpleGlobalVariable(id);
    }

}
