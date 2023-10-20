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

package ai.metaheuristic.ai.dispatcher.variable_global;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.storage.DispatcherBlobStorage;
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
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class GlobalVariableTxService {

    private final GlobalVariableRepository globalVariableRepository;
    private final Globals globals;
    private final DispatcherBlobStorage dispatcherBlobStorage;

    @Transactional(readOnly = true)
    public String getVariableDataAsString(Long variableId) {
        final String data = getVariableDataAsString(variableId, false);
        if (S.b(data)) {
            final String es = "#089.023 Variable data wasn't found, variableId: " + variableId;
            log.warn(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        return data;
    }

    @Nullable
    private String getVariableDataAsString(Long variableId, boolean nullable) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            dispatcherBlobStorage.accessGlobalVariableData(variableId, (is)-> {
                try {
                    IOUtils.copy(is, baos);
                } catch (IOException e) {
                    String es = "171.550 "+e;
                    log.error(es, e);
                    throw new VariableCommonException(es, variableId);
                }
            });
            return baos.toString(StandardCharsets.UTF_8);
/*
            Blob blob = globalVariableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                if (nullable) {
                    log.info("#089.025 Variable #{} is nullable and current value is null", variableId);
                    return null;
                }
                else {
                    final String es = "#089.027 Variable data wasn't found, variableId: " + variableId;
                    log.warn(es);
                    throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
                }
            }
            try (InputStream is = blob.getBinaryStream()) {
                String s = IOUtils.toString(is, StandardCharsets.UTF_8);
                return s;
            }
*/
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Throwable th) {
            log.error("#089.028", th);
            throw new VariableCommonException("#089.029 Error: " + th.getMessage(), variableId);
        }
    }

    @Transactional(readOnly = true)
    public void storeToFileWithTx(Long variableId, Path trgFile) {
        storeToFile(variableId, trgFile);
    }

    public void storeToFile(Long variableId, Path trgFile) {
        try {
            dispatcherBlobStorage.accessGlobalVariableData(variableId, (is)-> DirUtils.copy(is, trgFile));
/*
            Blob blob = globalVariableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                log.warn("#089.030 Binary data for variableId {} wasn't found", variableId);
                throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.global, "#089.040 Binary data wasn't found, variableId: " + variableId);
            }
            try (InputStream is = blob.getBinaryStream()) {
                DirUtils.copy(is, trgFile);
            }
*/
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
