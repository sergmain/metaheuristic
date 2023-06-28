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

package ai.metaheuristic.ai.dispatcher.cache;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.CacheProcess;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.dispatcher.event.events.ResourceCloseTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.*;
import ai.metaheuristic.ai.dispatcher.variable.VariableTopLevelService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
@RequiredArgsConstructor
public class CacheTxService {

    private final Globals globals;
    private final CacheProcessRepository cacheProcessRepository;
    private final CacheVariableService cacheVariableService;
    private final VariableTopLevelService variableTopLevelService;
    private final VariableTxService variableTxService;
    private final VariableRepository variableRepository;
    private final VariableBlobRepository variableBlobRepository;
    private final GlobalVariableRepository globalVariableRepository;
    private final CacheVariableRepository cacheVariableRepository;
    private final ApplicationEventPublisher eventPublisher;

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
            log.info("#611.200 process {} was already cached", tpy.task.processCode);
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
                throw new VariableCommonException("#611.040 ExecContext is broken, variable #"+output.id+" wasn't found", output.id);
            }
            if (v.nullified) {
                cacheVariableService.createAsNull(cacheProcess.id, output.name);
            }
            else {
                try {
                    tempFile = Files.createTempFile(globals.dispatcherTempPath, "var-" + output.id + "-", ".bin");
                } catch (IOException e) {
                    String es = "#611.060 Error: " + e.getMessage();
                    log.error(es, e);
                    throw new VariableCommonException(es, output.id);
                }
                variableTxService.storeToFile(output.id, tempFile);

                InputStream is;
                BufferedInputStream bis;
                try {
                    is = Files.newInputStream(tempFile); bis = new BufferedInputStream(is, 0x8000);
                } catch (IOException e) {
                    String es = "#611.080 Error: " + e.getMessage();
                    log.error(es, e);
                    eventPublisher.publishEvent(new ResourceCloseTxEvent(tempFile));
                    throw new VariableCommonException(es, output.id);
                }
                eventPublisher.publishEvent(new ResourceCloseTxEvent(List.of(bis, is), tempFile));
                final long size;
                try {
                    size = Files.size(tempFile);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
                cacheVariableService.createInitialized(cacheProcess.id, is, size, output.name);
            }
        }
    }

//    @Transactional(readOnly = true)
    public CacheData.FullKey getKey(TaskParamsYaml tpy, ExecContextParamsYaml.FunctionDefinition function) {
        return CacheUtils.getKey(tpy, function, variableTopLevelService::variableBlobIdRef, variableTxService::getVariableBlobDataAsString, variableBlobRepository::getDataAsStreamById, globalVariableRepository::getDataAsStreamById);
    }

}
