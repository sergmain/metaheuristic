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
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.ai.exceptions.StoreNewFileException;
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static ai.metaheuristic.api.EnumsApi.DataSourcing;

@Service
@Transactional
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class GlobalVariableService {

    private final EntityManager em;
    private final GlobalVariableRepository globalVariableRepository;
    private final Globals globals;

    @Transactional(readOnly = true)
    public GlobalVariable getBinaryData(Long id) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("this method intended to be only for test cases");
        }
        return getBinaryData(id, true);
    }

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
            throw new IllegalStateException("SQL error", e);
        }
    }

    public void storeToFile(Long id, File trgFile) {
        try {
            Blob blob = globalVariableRepository.getDataAsStreamById(id);
            if (blob==null) {
                log.warn("#087.010 Binary data for id {} wasn't found", id);
                throw new BinaryDataNotFoundException("#087.010 Binary data wasn't found, id: " + id);
            }
            try (InputStream is = blob.getBinaryStream()) {
                FileUtils.copyInputStreamToFile(is, trgFile);
            }
        } catch (BinaryDataNotFoundException e) {
            throw e;
        } catch (Exception e) {
            String es = "#087.020 Error while storing binary data";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    @Transactional(readOnly = true)
    public List<SimpleVariableAndStorageUrl> getIdInVariables(List<String> variables) {
        if (variables.isEmpty()) {
            return List.of();
        }
        return globalVariableRepository.getIdAndStorageUrlInVars(variables);
    }

    public void deleteByVariable(String variable) {
        globalVariableRepository.deleteByVariable(variable);
    }

    public GlobalVariable save(InputStream is, long size, String variable, String filename) {
        try {
            GlobalVariable data = new GlobalVariable();
            data.setVariable(variable);
            data.setFilename(filename);
            data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.launchpad)));
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));

            Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
            data.setData(blob);

            globalVariableRepository.save(data);

            return data;
        }
        catch(VariableSavingException | PessimisticLockingFailureException e) {
            throw e;
        } catch(Throwable th) {
            throw new VariableSavingException("#087.070 error storing data to db - " + th.getMessage(), th);
        }
    }

    public GlobalVariable storeInitialGlobalVariable(File tempFile, String variable, String filename) {
        try {
            try (InputStream is = new FileInputStream(tempFile)) {
                return save(is, tempFile.length(), variable, filename);
            }
        } catch (IOException e) {
            log.error("Error", e);
            throw new StoreNewFileException("Error while storing", e, tempFile.getPath(), filename);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public GlobalVariable createGlobalVariableWithExternalStorage(String variable, String params) {

        try {
            GlobalVariable data = new GlobalVariable();
            data.setVariable(variable);
            data.setFilename(null);
            data.setParams(params);
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));
            data.setData(null);
            globalVariableRepository.save(data);

            return data;
        }
        catch(IllegalStateException e) {
            throw e;
        }
        catch(Throwable th) {
            log.error("#087.100 error storing data to db", th);
            throw new RuntimeException("Error", th);
        }
    }

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

    @Transactional(readOnly = true)
    public Optional<GlobalVariable> findById(Long id) {
        return globalVariableRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Slice<SimpleGlobalVariable> getAllAsSimpleGlobalVariable(Pageable pageable) {
        return globalVariableRepository.getAllAsSimpleGlobalVariable(pageable);
    }

    @Transactional(readOnly = true)
    public SimpleGlobalVariable getByIdAsSimpleGlobalVariable(Long id) {
        return globalVariableRepository.getByIdAsSimpleGlobalVariable(id);
    }

    @Transactional(readOnly = true)
    public List<String> getFilenamesByVariable(String variable) {
        return globalVariableRepository.findFilenamesByVar(variable);
    }
}
