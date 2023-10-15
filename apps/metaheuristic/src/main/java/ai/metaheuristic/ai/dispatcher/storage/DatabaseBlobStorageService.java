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

package ai.metaheuristic.ai.dispatcher.storage;

import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionDataRepository;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableBlobRepository;
import ai.metaheuristic.ai.dispatcher.storage.variable.VariableDatabaseSpecificService;
import ai.metaheuristic.ai.exceptions.FunctionDataNotFoundException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author Sergio Lissner
 * Date: 8/17/2023
 * Time: 11:43 AM
 */
@SuppressWarnings("DuplicatedCode")
@Slf4j
@Service
@Profile({"dispatcher & !disk-storage"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DatabaseBlobStorageService implements DispatcherBlobStorage {

    private final VariableDatabaseSpecificService variableDatabaseSpecificService;
    private final VariableBlobRepository variableBlobRepository;
    private final DatabaseBlobPersistService databaseBlobStoreService;
    private final GlobalVariableRepository globalVariableRepository;
    private final FunctionDataRepository functionDataRepository;
    private final CacheVariableDatabaseStorageService cacheVariableDatabaseStorageService;

    @Override
    public void accessVariableData(Long variableBlobId, Consumer<InputStream> processBlobDataFunc) throws SQLException, IOException {
        TxUtils.checkTxExists();
        Blob blob = variableBlobRepository.getDataAsStreamById(Objects.requireNonNull(variableBlobId));
        if (blob==null) {
            String es = "174.040 Variable #"+ variableBlobId +" wasn't found";
            log.warn(es);
            throw new VariableDataNotFoundException(variableBlobId, EnumsApi.VariableContext.local, es);
        }
        try (InputStream is = blob.getBinaryStream(); BufferedInputStream bis = new BufferedInputStream(is)) {
            processBlobDataFunc.accept(bis);
        }
    }

    @SneakyThrows
    @Override
    public InputStream getVariableDataAsStreamById(Long variableBlobId) {
        Blob blob = variableBlobRepository.getDataAsStreamById(Objects.requireNonNull(variableBlobId));
        if (blob==null) {
            String es = "174.080 Variable #"+ variableBlobId +" wasn't found";
            log.warn(es);
            throw new VariableDataNotFoundException(variableBlobId, EnumsApi.VariableContext.local, es);
        }
        InputStream is = blob.getBinaryStream();
//        BufferedInputStream bis = new BufferedInputStream(is);
        return is;
    }

    public void storeVariableData(Long variableBlobId, InputStream is, long size ) {
        databaseBlobStoreService.storeVariable(variableBlobId, is, size);
    }

    @Override
    public void copyVariableData(VariableData.StoredVariable sourceVariable, TaskParamsYaml.OutputVariable targetVariable) {
        variableDatabaseSpecificService.copyData(sourceVariable, targetVariable);
    }

    @SneakyThrows
    @Override
    public InputStream getGlobalVariableDataAsStreamById(Long globalVariableId) {
        Blob blob = globalVariableRepository.getDataAsStreamById(Objects.requireNonNull(globalVariableId));
        if (blob==null) {
            String es = "174.240 Variable #"+ globalVariableId +" wasn't found";
            log.warn(es);
            throw new VariableDataNotFoundException(globalVariableId, EnumsApi.VariableContext.local, es);
        }
        InputStream is = blob.getBinaryStream();
//        BufferedInputStream bis = new BufferedInputStream(is);
        return is;
    }

    @Override
    public void accessGlobalVariableData(Long globalVariableId, Consumer<InputStream> processBlobDataFunc) throws SQLException, IOException {
        //TxUtils.checkTxExists();
        Blob blob = globalVariableRepository.getDataAsStreamById(Objects.requireNonNull(globalVariableId));
        if (blob==null) {
            String es = "174.340 Variable #"+ globalVariableId +" wasn't found";
            log.warn(es);
            throw new VariableDataNotFoundException(globalVariableId, EnumsApi.VariableContext.local, es);
        }
        try (InputStream is = blob.getBinaryStream(); BufferedInputStream bis = new BufferedInputStream(is)) {
            processBlobDataFunc.accept(bis);
        }
    }

    @Override
    public void storeGlobalVariableData(Long globalVariableId, InputStream is, long size) {
        databaseBlobStoreService.storeGlobalVariable(globalVariableId, is, size);

    }

    @Override
    public void accessFunctionData(String functionCode, Consumer<InputStream> processBlobDataFunc) throws SQLException, IOException {
        TxUtils.checkTxExists();
        Blob blob = functionDataRepository.getDataAsStreamByCode(functionCode);
        if (blob==null) {
            String es = "174.380 FunctionData #"+ functionCode +" wasn't found";
            log.warn(es);
            throw new FunctionDataNotFoundException(functionCode, es);
        }
        try (InputStream is = blob.getBinaryStream(); BufferedInputStream bis = new BufferedInputStream(is)) {
            processBlobDataFunc.accept(bis);
        }
    }

    @Override
    public void storeFunctionData(Long functionDataId, InputStream is, long size) {
        databaseBlobStoreService.storeFunctionData(functionDataId, is, size);

    }

    @Override
    public void storeCacheVariableData(Long cacheVariableId, InputStream is, long size) {
        databaseBlobStoreService.storeCacheVariableData(cacheVariableId, is, size);
    }

    @Override
    public void accessCacheVariableData(Long cacheVariableId, Consumer<InputStream> processBlobDataFunc) throws SQLException, IOException {
        cacheVariableDatabaseStorageService.accessCacheVariableData(cacheVariableId, processBlobDataFunc);
    }
}
