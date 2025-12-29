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

package ai.metaheuristic.ai.dispatcher.cache;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.CacheProcess;
import ai.metaheuristic.ai.dispatcher.beans.CacheVariable;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.dispatcher.event.events.ResourceCloseTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.CacheProcessRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CacheVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.commons.spi.DispatcherBlobStorage;
import ai.metaheuristic.commons.spi.GeneralBlobTxService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * @author Serge
 * Date: 10/27/2020
 * Time: 6:54 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class CacheTxService {

    private final Globals globals;
    private final CacheProcessRepository cacheProcessRepository;
    private final VariableService variableTopLevelService;
    private final VariableTxService variableTxService;
    private final VariableRepository variableRepository;
    private final CacheVariableRepository cacheVariableRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DispatcherBlobStorage dispatcherBlobStorage;
    private final GeneralBlobTxService generalBlobTxService;
    private final CacheBlobTxService cacheBlobTxService;

    @Transactional
    public void deleteCacheVariable(Long cacheProcessId) {
        cacheVariableRepository.deleteByCacheProcessId(cacheProcessId);
    }

    @Transactional
    public void deleteCacheProcesses(List<Long> page) {
        cacheProcessRepository.deleteAllByIdIn(page);
    }

    @Transactional
    public void storeVariablesTx(TaskParamsYaml tpy, ExecContextParamsYaml.FunctionDefinition function) {
        storeVariables(tpy, function);
    }

    public void storeVariables(TaskParamsYaml tpy, ExecContextParamsYaml.FunctionDefinition function) {
        TxUtils.checkTxExists();

        CacheData.FullKey fullKey = getKey(tpy, function);
        CacheData.SimpleKey key = CacheUtils.fullKeyToSimpleKey(fullKey);
        if (key==null) {
            return;
        }

        CacheProcess cacheProcess = cacheProcessRepository.findByKeySha256Length(key.key());

        if (cacheProcess != null) {
            log.info("611.020 process {} was already cached", tpy.task.processCode);
            return;
        }

        cacheProcess = new CacheProcess();
        cacheProcess.createdOn = System.currentTimeMillis();
        cacheProcess.functionCode = function.code;
        cacheProcess.keySha256Length = key.key();
        cacheProcess.keyValue = StringUtils.substring(key.keyAsStr(), 0, 510);
        cacheProcess = cacheProcessRepository.save(cacheProcess);

        for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
            final Path tempFile;

            Variable v = variableRepository.findByIdAsSimple(output.id);
            if (v==null) {
                throw new VariableCommonException("611.040 ExecContext is broken, variable #"+output.id+" wasn't found", output.id);
            }
            if (v.nullified) {
                cacheBlobTxService.createEmptyCacheVariable(cacheProcess.id, output.name);
                return;
            }


            try {
                tempFile = Files.createTempFile(globals.dispatcherTempPath, "var-" + output.id + "-", CommonConsts.BIN_EXT);
            } catch (IOException e) {
                String es = "611.060 Error: " + e;
                log.error(es, e);
                throw new VariableCommonException(es, output.id);
            }
            variableTxService.storeToFile(output.id, tempFile);

            InputStream is;
            BufferedInputStream bis;
            try {
                is = Files.newInputStream(tempFile); bis = new BufferedInputStream(is, 0x8000);
            } catch (IOException e) {
                String es = "611.080 Error: " + e;
                log.error(es, e);
                eventPublisher.publishEvent(new ResourceCloseTxEvent(tempFile));
                throw new VariableCommonException(es, output.id);
            }
            eventPublisher.publishEvent(new ResourceCloseTxEvent(List.of(bis, is), tempFile));
            final long size;
            try {
                size = Files.size(tempFile);
                CacheVariable cacheVariable = cacheBlobTxService.createEmptyCacheVariable(cacheProcess.id, output.name);
                dispatcherBlobStorage.storeCacheVariableData(cacheVariable.id, is, size);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public CacheData.FullKey getKey(TaskParamsYaml tpy, ExecContextParamsYaml.FunctionDefinition function) {
        return CacheUtils.getKey(tpy, function.params,
            variableTopLevelService::variableBlobIdRef,
            variableTxService::getVariableBlobDataAsString,
            dispatcherBlobStorage::getVariableDataAsStreamById,
            dispatcherBlobStorage::getGlobalVariableDataAsStreamById);
    }

}
