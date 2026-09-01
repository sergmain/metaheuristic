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

package ai.metaheuristic.ai.dispatcher.variable_global;

import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.commons.spi.DispatcherBlobStorage;
import ai.metaheuristic.commons.spi.GeneralBlobTxService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class GlobalVariableTxService {

    private final GlobalVariableRepository globalVariableRepository;
    private final DispatcherBlobStorage dispatcherBlobStorage;
    private final GeneralBlobTxService generalBlobTxService;

    /**
     * Stores new content for a global variable.
     *
     * <p>WORM: a VariableBlob is written once, so this never overwrites the current payload. It
     * allocates a fresh blob, re-points the row at it, and only then releases the one the row used to
     * reference - which is the "re-execution allocates a NEW VariableBlob and the referrer re-points"
     * rule that DatabaseBlobPersistService.storeVariable enforces.
     */
    @Transactional
    public void storeData(Long globalVariableId, InputStream is, long size) {
        final Long newVariableBlobId = generalBlobTxService.createEmptyVariable(DispatcherBlobStorage.KIND_MH);
        dispatcherBlobStorage.storeVariableData(newVariableBlobId, is, size, DispatcherBlobStorage.KIND_MH);

        GlobalVariable gv = globalVariableRepository.findByIdForUpdate(globalVariableId);
        if (gv==null) {
            throw new VariableCommonException("089.020 globalVariable not found", globalVariableId);
        }
        final Long oldVariableBlobId = gv.variableBlobId;
        gv.variableBlobId = newVariableBlobId;
        gv.uploadTs = new Timestamp(System.currentTimeMillis());
        globalVariableRepository.save(gv);

        if (oldVariableBlobId!=null) {
            dispatcherBlobStorage.deleteVariableData(oldVariableBlobId);
        }
    }

    // resolving the global variable to its VariableBlob belongs here rather than in each storage
    // backend: the payload is an ordinary VariableBlob, so the backends only ever needed the anchor.
    //
    // @Transactional because accessVariableData asserts an active tx, which the global-variable read
    // never used to - the DB backend had its checkTxExists() commented out. The in-class callers below
    // are already transactional and reach this by self-invocation, so the annotation is what covers a
    // caller from outside.
    @Transactional(readOnly = true)
    public void accessData(Long globalVariableId, Consumer<InputStream> processBlobDataFunc) throws SQLException, IOException {
        dispatcherBlobStorage.accessVariableData(variableBlobIdNotNull(globalVariableId), processBlobDataFunc);
    }

    @Transactional(readOnly = true)
    public InputStream getDataAsStreamById(Long globalVariableId) {
        return dispatcherBlobStorage.getVariableDataAsStreamById(variableBlobIdNotNull(globalVariableId));
    }

    private Long variableBlobIdNotNull(Long globalVariableId) {
        final Long variableBlobId = globalVariableRepository.findVariableBlobIdById(globalVariableId);
        if (variableBlobId==null) {
            final String es = "089.021 Global variable #" + globalVariableId + " has no stored payload";
            log.warn(es);
            throw new VariableDataNotFoundException(globalVariableId, EnumsApi.VariableContext.global, es);
        }
        return variableBlobId;
    }

    @Transactional(readOnly = true)
    public String getVariableDataAsString(Long variableId) {
        final String data = getVariableDataAsString(variableId, false);
        if (S.b(data)) {
            final String es = "089.040 Variable data wasn't found, variableId: " + variableId;
            log.warn(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        return data;
    }

    @Nullable
    private String getVariableDataAsString(Long variableId, boolean nullable) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            accessData(variableId, (is)-> {
                try {
                    IOUtils.copy(is, baos);
                } catch (IOException e) {
                    String es = "089.080 "+e;
                    log.error(es, e);
                    throw new VariableCommonException(es, variableId);
                }
            });
            return baos.toString(StandardCharsets.UTF_8);
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Throwable th) {
            log.error("089.0120", th);
            throw new VariableCommonException("089.140 Error: " + th.getMessage(), variableId);
        }
    }

    @Transactional(readOnly = true)
    public void storeToFileWithTx(Long variableId, Path trgFile) {
        storeToFile(variableId, trgFile);
    }

    public void storeToFile(Long variableId, Path trgFile) {
        try {
            accessData(variableId, (is)-> DirUtils.copy(is, trgFile));
/*
            Blob blob = globalVariableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                log.warn("089.030 Binary data for variableId {} wasn't found", variableId);
                throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.global, "089.040 Binary data wasn't found, variableId: " + variableId);
            }
            try (InputStream is = blob.getBinaryStream()) {
                DirUtils.copy(is, trgFile);
            }
*/
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Exception e) {
            String es = "089.160 Error while storing binary data";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    @Transactional
    public void deleteByVariable(String variable) {
        final List<Long> variableBlobIds = globalVariableRepository.findVariableBlobIdsByName(variable);
        globalVariableRepository.deleteByName(variable);
        for (Long variableBlobId : variableBlobIds) {
            dispatcherBlobStorage.deleteVariableData(variableBlobId);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    @Transactional
    public GlobalVariable createGlobalVariableWithExternalStorage(String variable, String params) {

        GlobalVariable data = new GlobalVariable();
        data.setName(variable);
        data.setFilename(null);
        data.setParams(params);
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));
        // external storage: the location lives in PARAMS, there are no dispatcher-held bytes
        data.variableBlobId = null;
        globalVariableRepository.save(data);

        return data;
    }

    public Page<GlobalVariable> findAll(Pageable pageable) {
        return globalVariableRepository.findAll(pageable);
    }

    @Transactional
    public void deleteById(Long id) {
        // capture the anchor before the row goes - afterwards nothing knows which blob this was
        final Long variableBlobId = globalVariableRepository.findVariableBlobIdById(id);
        globalVariableRepository.deleteById(id);
        if (variableBlobId!=null) {
            dispatcherBlobStorage.deleteVariableData(variableBlobId);
        }
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
