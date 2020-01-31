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

package ai.metaheuristic.ai.launchpad.binary_data;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.exceptions.BinaryDataSaveException;
import ai.metaheuristic.ai.exceptions.StoreNewFileException;
import ai.metaheuristic.ai.launchpad.beans.BinaryData;
import ai.metaheuristic.ai.launchpad.launchpad_resource.SimpleVariable;
import ai.metaheuristic.ai.launchpad.repositories.BinaryDataRepository;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
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
@Profile("launchpad")
@RequiredArgsConstructor
public class BinaryDataService {

    private final EntityManager em;
    private final BinaryDataRepository binaryDataRepository;
    private final Globals globals;

    @Transactional(readOnly = true)
    public BinaryData getBinaryData(Long id) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("this method intended to be only for test cases");
        }
        return getBinaryData(id, true);
    }

    private BinaryData getBinaryData(Long id, @SuppressWarnings("SameParameterValue") boolean isInitBytes) {
        try {
            BinaryData data = binaryDataRepository.findById(id).orElse(null);
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

    public void storeToFile(String id, File trgFile) {
        try {
            Blob blob = binaryDataRepository.getDataAsStreamByCode(Long.valueOf(id));
            if (blob==null) {
                log.warn("#087.010 Binary data for id {} wasn't found", id);
                throw new BinaryDataNotFoundException("#087.010 Binary data wasn't found, code: " + id);
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

    public void deleteByWorkbookId(Long workbookId) {
        binaryDataRepository.deleteByWorkbookId(workbookId);
    }

    @Transactional(readOnly = true)
    public List<SimpleVariableAndStorageUrl> getIdInVariables(List<String> variables, Long workbookId) {
        if (variables.isEmpty()) {
            return List.of();
        }
        return binaryDataRepository.getIdAndStorageUrlInVarsForWorkbook(variables, workbookId);
    }

    @Transactional(readOnly = true)
    public List<SimpleVariableAndStorageUrl> getIdInVariables(List<String> variables) {
        if (variables.isEmpty()) {
            return List.of();
        }
        return binaryDataRepository.getIdAndStorageUrlInVars(variables);
    }

/*
    public void deleteByCodeAndDataType(String code) {
        binaryDataRepository.deleteByCodeAndDataType(code);
    }
*/

    public void deleteByVariable(String variable) {
        binaryDataRepository.deleteByVariable(variable);
    }

    public BinaryData save(InputStream is, long size, String variable, String filename, Long workbookId, String contextId) {
        try {
            BinaryData data = new BinaryData();
            data.setVariable(variable);
            data.setFilename(filename);
            data.setWorkbookId(workbookId);
            data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.launchpad)));
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));
            data.setContextId(contextId);

            Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
            data.setData(blob);

            binaryDataRepository.save(data);

            return data;
        }
        catch(BinaryDataSaveException | PessimisticLockingFailureException e) {
            throw e;
        } catch(Throwable th) {
            throw new BinaryDataSaveException("#087.070 error storing data to db - " + th.getMessage(), th);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public BinaryData saveWithSpecificStorageUrl(String variable, String params) {
        try {
            BinaryData data = new BinaryData();
            data.setVariable(variable);
            data.setFilename(null);
            data.setWorkbookId(null);
            data.setParams(params);
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));
            data.setData(null);
            binaryDataRepository.save(data);

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

    public void update(InputStream is, long size, BinaryData data) {
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);

        binaryDataRepository.save(data);
    }

    public Page<BinaryData> findAll(Pageable pageable) {
        return binaryDataRepository.findAll(pageable);
    }

    public void deleteById(Long id) {
        binaryDataRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Optional<BinaryData> findById(Long id) {
        return binaryDataRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Slice<SimpleVariable> getAllAsSimpleResources(Pageable pageable) {
        return binaryDataRepository.getAllAsSimpleResources(pageable);
    }

    @Transactional(readOnly = true)
    public List<String> getFilenameByVariableAndWorkbookId(String variable, Long workbookId) {
        return binaryDataRepository.findFilenameByVariableAndWorkbookId(variable, workbookId);
    }

    @Transactional(readOnly = true)
    public List<String> findFilenameByBatchId(Long batchId, Long workbookId) {
        if (true) {
            throw new NotImplementedException("Need to re-write");
        }
        return binaryDataRepository.findFilenameByVariableAndWorkbookId(batchId.toString(), workbookId);
    }

    @Transactional(readOnly = true)
    public List<Object[]> getFilenamesForBatchIds(List<Long> batchIds) {
        if (batchIds.isEmpty()) {
            return List.of();
        }
        if (true) {
            throw new NotImplementedException("Need to re-write");
        }
        return binaryDataRepository.getFilenamesForBatchIds(batchIds);
    }

    public BinaryData storeInitialResource(File tempFile, String variable, String filename, Long workbookId, String contextId) {
        try {
            try (InputStream is = new FileInputStream(tempFile)) {
                return save(is, tempFile.length(), variable, filename, workbookId, contextId);
            }
        } catch (IOException e) {
            log.error("Error", e);
            throw new StoreNewFileException("Error while storing", e, tempFile.getPath(), filename);
        }
    }}
