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

package ai.metaheuristic.ai.dispatcher.cache;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.CacheProcess;
import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.dispatcher.repositories.CacheProcessRepository;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.CountingInputStream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.*;
import java.sql.Blob;
import java.util.function.Function;

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
public class CacheService {

    private final Globals globals;
    private final CacheProcessRepository cacheProcessRepository;
    private final CacheVariableService cacheVariableService;
    private final VariableService variableService;
    private final VariableRepository variableRepository;
    private final GlobalVariableRepository globalVariableRepository;

    public void storeVariables(TaskParamsYaml tpy, DataHolder holder) {

        CacheData.Key fullKey = getKey(tpy);

        String keyAsStr = fullKey.asString();
        byte[] bytes = keyAsStr.getBytes();

        CacheProcess cacheProcess=null;
        String key = null;
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            String sha256 = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, is);
            key = new CacheData.Sha256PlusLength(sha256, keyAsStr.length()).asString();
            cacheProcess = cacheProcessRepository.findByKeySha256Length(key);
        } catch (IOException e) {
            log.error("#611.020 Error while preparing a cache key, task will be processed without cached data", e);
        }

        if (cacheProcess==null) {

            cacheProcess = new CacheProcess();
            cacheProcess.createdOn = System.currentTimeMillis();
            cacheProcess.keySha256Length = key;
            cacheProcess.keyValue = StringUtils.substring(keyAsStr, 0, 510);
            cacheProcess = cacheProcessRepository.save(cacheProcess);

            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                final File tempFile;

                SimpleVariable simple = variableRepository.findByIdAsSimple(output.id);
                if (simple==null) {
                    throw new VariableCommonException("#611.040 ExecContext is broken, variable #"+output.id+" wasn't found", output.id);
                }
                if (simple.nullified) {
                    cacheVariableService.createAsNull(cacheProcess.id, output.name);
                }
                else {
                    try {
                        tempFile = File.createTempFile("var-" + output.id + "-", ".bin", globals.dispatcherTempDir);
                    } catch (IOException e) {
                        String es = "#611.060 Error: " + e.getMessage();
                        log.error(es, e);
                        throw new VariableCommonException(es, output.id);
                    }
                    holder.files.add(tempFile);
                    variableService.storeToFile(output.id, tempFile);

                    InputStream is;
                    try {
                        is = new FileInputStream(tempFile);
                    } catch (IOException e) {
                        String es = "#611.080 Error: " + e.getMessage();
                        log.error(es, e);
                        throw new VariableCommonException(es, output.id);
                    }
                    holder.inputStreams.add(is);
                    cacheVariableService.createInitialized(cacheProcess.id, is, tempFile.length(), output.name);
                }
            }
        }
        else {
            log.info("#611.200 process {} was already cached", tpy.task.processCode);
        }

    }

    public CacheData.Key getKey(TaskParamsYaml tpy) {
        return getKey(tpy, variableService::getVariableDataAsString, variableRepository::getDataAsStreamById, globalVariableRepository::getDataAsStreamById);
    }

    public static CacheData.Key getKey(
            TaskParamsYaml tpy,
            Function<Long, String> variableAsString, Function<Long, Blob> variableAsStream, Function<Long, Blob> globalVariableAsStream) {
        boolean paramsAsContent = MetaUtils.isTrue(tpy.task.function.metas, ConstsApi.META_MH_FUNCTION_PARAMS_AS_FILE_META);

        CacheData.Key fullKey = new CacheData.Key(tpy.task.function.code, paramsAsContent ? "" : tpy.task.function.params);
        if (tpy.task.inline!=null) {
            fullKey.inline.putAll(tpy.task.inline);
        }
        for (TaskParamsYaml.InputVariable input : tpy.task.inputs) {
            if (input.context== EnumsApi.VariableContext.array) {
                String data = variableAsString.apply(input.id);
                VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(data);
                for (VariableArrayParamsYaml.Variable variable : vapy.array) {
                    if (variable.dataType== EnumsApi.DataType.variable) {
                        long variableId = Long.parseLong(variable.id);
                        fullKey.inputs.add(getSha256Length(variableId, variableAsStream));
                    }
                    else {
                        fullKey.inputs.add(getSha256Length(input.id, globalVariableAsStream));
                    }
                }
            }
            else {
                if (input.context== EnumsApi.VariableContext.local) {
                    fullKey.inputs.add(getSha256Length(input.id, variableAsStream));
                }
                else {
                    fullKey.inputs.add(getSha256Length(input.id, globalVariableAsStream));
                }
            }
        }
        fullKey.inputs.sort(CacheData.SHA_256_PLUS_LENGTH_COMPARATOR);
        return fullKey;
    }

    private static CacheData.Sha256PlusLength getSha256Length(Long variableId, Function<Long, Blob> function) {
        try {
            Blob blob = function.apply(variableId);
            if (blob==null) {
                String es = S.f("#171.320 Data for variableId #%d wasn't found", variableId);
                log.warn(es);
                throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
            }
            try (InputStream is = blob.getBinaryStream(); CountingInputStream cis = new CountingInputStream(is)) {
                String sha256 = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, cis);
                long length = cis.getBytesRead();
                return new CacheData.Sha256PlusLength(sha256, length);
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Throwable e) {
            String es = "#171.340 Error while storing data to file";
            log.error(es, e);
            throw new VariableCommonException(es, variableId);
        }
    }
}
