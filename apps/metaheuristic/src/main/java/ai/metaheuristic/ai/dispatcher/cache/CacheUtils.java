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

import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.CountingInputStream;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * @author Sergio Lissner
 * Date: 5/30/2023
 * Time: 1:14 AM
 */
@Slf4j
public class CacheUtils {



    public static CacheData.FullKey getKey(
            TaskParamsYaml tpy,
            ExecContextParamsYaml.FunctionDefinition function,
            Function<Long, String> variableAsString, Function<Long, Blob> variableAsStream, Function<Long, Blob> globalVariableAsStream) {

        String params = S.b(tpy.task.function.params) ? "" : tpy.task.function.params;
        if (!S.b(function.params)) {
            params = params + " " + function.params;
        }
        CacheData.FullKey fullKey = new CacheData.FullKey(tpy.task.function.code, params);
        if (tpy.task.inline!=null) {
            if (tpy.task.cache == null || !tpy.task.cache.omitInline) {
                fullKey.inline.putAll(tpy.task.inline);
            }
        }
        if (tpy.task.cache!=null && tpy.task.cache.cacheMeta) {
            for (Map<String, String> meta : tpy.task.metas) {
                if (meta.size()!=1) {
                    throw new IllegalStateException("(meta.size()!=1)");
                }
                Map.Entry<String, String> entry = meta.entrySet().stream().findFirst().orElse(null);
                fullKey.metas.add(getSha256PlusLength(new ByteArrayInputStream((entry.getKey()+"###"+entry.getValue()).getBytes(StandardCharsets.UTF_8))));
            }
        }
        for (TaskParamsYaml.InputVariable input : tpy.task.inputs) {
            switch (input.context) {
                case array -> {
                    String data = variableAsString.apply(input.id);
                    VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(data);
                    for (VariableArrayParamsYaml.Variable variable : vapy.array) {
                        if (variable.dataType==EnumsApi.DataType.variable) {
                            long variableId = Long.parseLong(variable.id);
                            fullKey.inputs.add(getSha256Length(variableId, variableAsStream));
                        }
                        else {
                            fullKey.inputs.add(getSha256Length(input.id, globalVariableAsStream));
                        }
                    }
                }
                case local -> fullKey.inputs.add(getSha256Length(input.id, variableAsStream));
                case global -> fullKey.inputs.add(getSha256Length(input.id, globalVariableAsStream));
                default -> throw new IllegalStateException("input.context " + input.context);
            }

        }
        fullKey.inputs.sort(CacheData.SHA_256_PLUS_LENGTH_COMPARATOR);
        fullKey.metas.sort(CacheData.SHA_256_PLUS_LENGTH_COMPARATOR);
        return fullKey;
    }

    private static CacheData.Sha256PlusLength getSha256Length(Long variableId, Function<Long, Blob> function) {
        try {
            Blob blob = function.apply(variableId);
            if (blob==null) {
                String es = S.f("#611.320 Data for variableId #%d wasn't found", variableId);
                log.warn(es);
                throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
            }
            try (InputStream is = blob.getBinaryStream(); BufferedInputStream bis = new BufferedInputStream(is, 0x8000)) {
                return getSha256PlusLength(bis);
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Throwable e) {
            String es = "#611.340 Error while storing data to file";
            log.error(es, e);
            throw new VariableCommonException(es, variableId);
        }
    }

    public static CacheData.Sha256PlusLength getSha256PlusLength(InputStream bis) {
        CountingInputStream cis = new CountingInputStream(bis);
        String sha256 = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, cis);
        long length = cis.getBytesRead();
        return new CacheData.Sha256PlusLength(sha256, length);
    }

    @Nullable
    public static CacheData.SimpleKey fullKeyToSimpleKey(CacheData.FullKey fullKey) {
        String keyAsStr = fullKey.asString();
        byte[] bytes = keyAsStr.getBytes();

        String key=null;
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            String sha256 = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, is);
            key = new CacheData.Sha256PlusLength(sha256, keyAsStr.length()).asString();
        }
        catch (IOException e) {
            log.error("609.040 Error while preparing a cache key, task will be processed without cached data", e);
        }
        return key==null ? null : new CacheData.SimpleKey(key, keyAsStr);
    }
}
