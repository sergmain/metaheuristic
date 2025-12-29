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

package ai.metaheuristic.ai.dispatcher.storage;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.CacheVariable;
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.repositories.CacheVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionDataRepository;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.exceptions.FunctionDataErrorException;
import ai.metaheuristic.ai.exceptions.FunctionDataNotFoundException;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.spi.DispatcherBlobStorage;
import ai.metaheuristic.commons.spi.StoredVariable;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.DirUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.function.Consumer;

/**
 * @author Sergio Lissner
 * Date: 8/17/2023
 * Time: 11:52 AM
 */
@Slf4j
@Service
@Profile({"dispatcher & disk-storage"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DiskBlobStorageService implements DispatcherBlobStorage {

    @Nullable
    public static Path getPath(Path basePath, Long id) {
        final Path path = getPoweredPath(basePath, id);
        if (Files.notExists(path)) {
            return null;
        }
        return path;
    }

    public static Path getPoweredPath(Path basePath, Long id) {
        return DirUtils.getPoweredPath(basePath, id);
    }

    public static class DataStorage {
        public final Path basePath;

        public DataStorage(Path basePath) {
            this.basePath = basePath;
        }

        public void accessData(Long id, Consumer<InputStream> processBlobDataFunc) throws IOException {
            final Path path = getPath(basePath, id);
            if (path==null) {
                String es = "176.040 Variable #"+ id +" wasn't found";
                log.warn(es);
                throw new VariableDataNotFoundException(id, EnumsApi.VariableContext.local, es);
            }
            Path dataPath = path.resolve(id + CommonConsts.BIN_EXT);
            try (InputStream is = Files.newInputStream(dataPath); BufferedInputStream bis = new BufferedInputStream(is, 0x1000)) {
                processBlobDataFunc.accept(bis);
            }
        }

        public InputStream getStreamById(Long id) throws IOException {
            final Path path = getPath(basePath, id);
            if (path==null) {
                String es = "176.080 Variable #"+ id +" wasn't found";
                log.warn(es);
                throw new VariableDataNotFoundException(id, EnumsApi.VariableContext.local, es);
            }
            Path dataPath = path.resolve(id + CommonConsts.BIN_EXT);
            return Files.newInputStream(dataPath);
        }

        @SuppressWarnings("unused")
        public void storeData(Long id, InputStream is, long size) throws IOException {
            final Path path = getPoweredPath(basePath, id);
            if (Files.notExists(path)) {
                Files.createDirectories(path);
            }
            Path dataPath = path.resolve(id + CommonConsts.BIN_EXT);
            try (OutputStream os = Files.newOutputStream(dataPath); BufferedOutputStream bos = new BufferedOutputStream(os)) {
                IOUtils.copyLarge(is, bos);
                bos.flush();
            }

            int i=0;
        }
    }

    private final Globals globals;
    private final FunctionDataRepository functionDataRepository;
    private final CacheVariableRepository cacheVariableRepository;
    private final VariableRepository variableRepository;
    private final GeneralBlobService generalBlobService;
    private final GlobalVariableRepository globalVariableRepository;

    private DataStorage dataStorageVariable;
    private DataStorage dataStorageGlobalVariable;
    private DataStorage dataStorageFunction;
    private DataStorage dataStorageCacheVariable;

    @PostConstruct
    public void init() {
        dataStorageVariable = new DataStorage(globals.getDispatcherStorageVariablesPath());
        dataStorageGlobalVariable = new DataStorage(globals.getDispatcherStorageGlobalVariablesPath());
        dataStorageFunction = new DataStorage(globals.getDispatcherStorageFunctionsPath());
        dataStorageCacheVariable = new DataStorage(globals.getDispatcherStorageCacheVariablessPath());
    }

    @Override
    public void accessVariableData(Long variableBlobId, Consumer<InputStream> processBlobDataFunc) throws IOException {
        dataStorageVariable.accessData(variableBlobId, processBlobDataFunc);
    }


    @SneakyThrows
    @Override
    public InputStream getVariableDataAsStreamById(Long variableBlobId) {
        return dataStorageVariable.getStreamById(variableBlobId);
    }

    @SneakyThrows
    @Override
    public void storeVariableData(Long variableBlobId, InputStream is, long size) {
        dataStorageVariable.storeData(variableBlobId, is, size);
    }

    @SneakyThrows
    @Override
    public void copyVariableData(StoredVariable sourceVariable, TaskParamsYaml.OutputVariable targetVariable) {
        Variable trg = variableRepository.findById(targetVariable.id).orElse(null);
        if (trg==null) {
            log.warn("!!! trying to copy date to non-existed variable");
            return;
        }

        trg.variableBlobId = generalBlobService.createVariableIfNotExist(trg.variableBlobId);
        if (trg.variableBlobId==null) {
            throw new IllegalStateException("(trg.variableBlobId==null)");
        }

        // that's correct - targetVariable.filename
        trg.filename = targetVariable.filename;
        trg.uploadTs = new Timestamp(System.currentTimeMillis());
        trg.inited = true;
        trg.nullified = false;
        variableRepository.save(trg);

        dataStorageCacheVariable.accessData(sourceVariable.id, (is)-> {
            try {
                dataStorageVariable.storeData(trg.variableBlobId, is, -1);
            } catch (IOException e) {
                throw new RuntimeException("176.120 error", e);
            }
        });

    }

    @SneakyThrows
    @Override
    public InputStream getGlobalVariableDataAsStreamById(Long globalVariableId) {
        return dataStorageGlobalVariable.getStreamById(globalVariableId);
    }

    @Override
    public void accessGlobalVariableData(Long globalVariableId, Consumer<InputStream> processBlobDataFunc) throws IOException {
        dataStorageGlobalVariable.accessData(globalVariableId, processBlobDataFunc);
    }

    @Override
    @Transactional
    public void storeGlobalVariableData(Long globalVariableId, InputStream is, long size) throws IOException {
        GlobalVariable globalVariable = globalVariableRepository.findById(globalVariableId).orElse(null);
        if (globalVariable==null) {
            throw new VariableCommonException("176.160 globalVariable not found", globalVariableId);
        }
        globalVariable.uploadTs = new Timestamp(System.currentTimeMillis());
        GlobalVariable result = globalVariableRepository.save(globalVariable);
        dataStorageGlobalVariable.storeData(globalVariableId, is, size);
    }

    @Override
    public void accessFunctionData(String functionCode, Consumer<InputStream> processBlobDataFunc) throws IOException {
        Long functionId = functionDataRepository.findIdByCode(functionCode);
        if (functionId == null) {
            throw new FunctionDataErrorException(functionCode, "176.200 error");
        }
        dataStorageFunction.accessData(functionId, processBlobDataFunc);
    }

    @SneakyThrows
    @Override
    public void storeFunctionData(Long functionDataId, InputStream is, long size) {
        dataStorageFunction.storeData(functionDataId, is, size);
    }

    @SneakyThrows
    @Override
    @Transactional
    public void storeCacheVariableData(Long cacheVariableId, InputStream is, long size) {
        dataStorageCacheVariable.storeData(cacheVariableId, is, size);
        CacheVariable cacheVariable = cacheVariableRepository.findById(cacheVariableId).orElse(null);
        if (cacheVariable==null) {
            throw new FunctionDataNotFoundException("id#"+cacheVariableId, "176.240 cacheVariable not found");
        }
        cacheVariable.createdOn = System.currentTimeMillis();
        cacheVariable.nullified = false;
        CacheVariable result = cacheVariableRepository.save(cacheVariable);
    }

    @SneakyThrows
    @Override
    public void accessCacheVariableData(Long cacheVariableId, Consumer<InputStream> processBlobDataFunc) {
        dataStorageCacheVariable.accessData(cacheVariableId, processBlobDataFunc);

    }
}
