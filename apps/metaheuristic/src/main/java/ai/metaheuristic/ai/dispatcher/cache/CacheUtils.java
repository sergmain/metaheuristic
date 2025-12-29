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

import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.SecUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BoundedInputStream;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

import static ai.metaheuristic.api.EnumsApi.VariableContext.global;

/**
 * @author Sergio Lissner
 * Date: 5/30/2023
 * Time: 1:14 AM
 */
@Slf4j
// TODO 2023-06-07 P0 add unit-tests
public class CacheUtils {

    public static CacheData.FullKey getKey(
            TaskParamsYaml tpy,
            @Nullable String functionParams,
            Function<Long, @Nullable Long> variableBlobIdRefFunc,
            Function<Long, String> variableAsStringFunc,
            Function<Long, InputStream> variableAsStreamFunc,
            Function<Long, InputStream> globalVariableAsStreamFunc) {

        String params = initParas(tpy, functionParams);

        CacheData.FullKey fullKey = new CacheData.FullKey(tpy.task.function.code, params);

        collectInlines(tpy, fullKey);
        addMetasIfNeeded(tpy, fullKey);
        collectChecksums(tpy, variableBlobIdRefFunc, variableAsStringFunc, variableAsStreamFunc, globalVariableAsStreamFunc, fullKey);

        fullKey.inputs.sort(CacheData.SHA_256_PLUS_LENGTH_COMPARATOR);
        fullKey.metas.sort(CacheData.SHA_256_PLUS_LENGTH_COMPARATOR);
        return fullKey;
    }

    private static void collectChecksums(TaskParamsYaml tpy, Function<Long, Long> variableBlobIdRefFunc, Function<Long, String> variableAsStringFunc,
                                         Function<Long, InputStream> variableAsStreamFunc, Function<Long, InputStream> globalVariableAsStreamFunc, CacheData.FullKey fullKey) {
        for (TaskParamsYaml.InputVariable input : tpy.task.inputs) {
            if (input.context==global) {
                fullKey.inputs.add(getSha256Length(input.id, globalVariableAsStreamFunc));
                continue;
            }

            Long variableBlobId = variableBlobIdRefFunc.apply(input.id);
            if (variableBlobId==null) {
                throw new IllegalStateException("(variableBlobId==null)");
            }

            switch (input.context) {
                case array -> {
                    String data = variableAsStringFunc.apply(variableBlobId);
                    VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(data);
                    for (VariableArrayParamsYaml.Variable variable : vapy.array) {
                        long variableId = Long.parseLong(variable.id);
                        if (variable.dataType==EnumsApi.DataType.variable) {
                            fullKey.inputs.add(getSha256Length(variableId, variableAsStreamFunc));
                        }
                        else {
                            fullKey.inputs.add(getSha256Length(variableId, globalVariableAsStreamFunc));
                        }
                    }
                }
                case local -> fullKey.inputs.add(getSha256Length(variableBlobId, variableAsStreamFunc));
                default -> throw new IllegalStateException("input.context " + input.context);
            }
        }
    }

    private static void addMetasIfNeeded(TaskParamsYaml tpy, CacheData.FullKey fullKey) {
        if (tpy.task.cache!=null && tpy.task.cache.cacheMeta) {
            for (Map<String, String> meta : tpy.task.metas) {
                if (meta.size()!=1) {
                    throw new IllegalStateException("181.020 (meta.size()!=1)");
                }
                Map.Entry<String, String> entry = meta.entrySet().stream().findFirst().orElse(null);
                fullKey.metas.add(getSha256PlusLength(new ByteArrayInputStream((entry.getKey() + SecUtils.SIGNATURE_DELIMITER + entry.getValue()).getBytes(StandardCharsets.UTF_8))));
            }
        }
    }

    private static void collectInlines(TaskParamsYaml tpy, CacheData.FullKey fullKey) {
        if (tpy.task.inline!=null) {
            if (tpy.task.cache == null || !tpy.task.cache.omitInline) {
                fullKey.inline.putAll(tpy.task.inline);
            }
        }
    }

    private static String initParas(TaskParamsYaml tpy, @Nullable String functionParams) {
        String params = S.b(tpy.task.function.params) ? "" : tpy.task.function.params;
        if (!S.b(functionParams)) {
            params = params + " " + functionParams;
        }
        return params;
    }

    private static CacheData.Sha256PlusLength getSha256Length(Long variableId, Function<Long, InputStream> streamFunction) {
        try {
            try (InputStream is = streamFunction.apply(variableId)) {
                return getSha256PlusLength(is);
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Throwable e) {
            String es = "181.040 Error while storing data to file";
            log.error(es, e);
            throw new VariableCommonException(es, variableId);
        }
    }

    @SneakyThrows
    public static CacheData.Sha256PlusLength getSha256PlusLength(InputStream bis) {
        BoundedInputStream boundedStream = BoundedInputStream.builder()
            .setInputStream(bis)
            .setMaxCount(Long.MAX_VALUE)
            .get();

        String sha256 = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, boundedStream);
        long length = boundedStream.getCount();

        return new CacheData.Sha256PlusLength(sha256, length);
    }

    @SuppressWarnings("ConstantValue")
    public static CacheData.@Nullable SimpleKey fullKeyToSimpleKey(CacheData.FullKey fullKey) {
        String keyAsStr = fullKey.asString();
        byte[] bytes = keyAsStr.getBytes();

        String key=null;
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            String sha256 = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, is);
            key = new CacheData.Sha256PlusLength(sha256, keyAsStr.length()).asString();
        }
        catch (IOException e) {
            log.error("181.080 Error while preparing a cache key, task will be processed without cached data", e);
        }
        return key==null ? null : new CacheData.SimpleKey(key, keyAsStr);
    }
}
