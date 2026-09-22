/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the dispatcher's cache key does with a NULLIFIED input - "no value" passed at launch.
 *
 * <p>{@link CacheUtils#getKey} keys an input by its content. A nullified input has none: MH creates it with
 * {@code variableBlobId == null} ({@code VariableTxService.createInitializedWithNull}). So the key must not try to
 * read it, and every run that passes the same input as null has to land on the same cache entry - the claim
 * DAHF §0.7 makes about a nullable ExecContext-level input.
 *
 * <p>No doubles: the key is computed by the production {@link CacheUtils#getKey}, through the same function
 * parameters production fills with its services. The store behind them is one fixed in-memory map, and a read the
 * Task must never make fails loudly instead of answering.
 */
@Execution(ExecutionMode.CONCURRENT)
public class CacheUtilsNullifiedInputTest {

    private static final String CODE = "fn-cached-with-nullable-input:1.0";
    private static final long INPUT_VARIABLE_ID = 11L;
    private static final long INPUT_BLOB_ID = 101L;

    @Test
    public void test_twoRunsWithTheInputNullified_shareOneKey() {
        final CacheData.SimpleKey first = CacheUtils.fullKeyToSimpleKey(keyOf(null));
        final CacheData.SimpleKey second = CacheUtils.fullKeyToSimpleKey(keyOf(null));

        assertNotNull(first, "a Task with a nullified input must still get a cache key");
        assertNotNull(second, "a Task with a nullified input must still get a cache key");
        assertEquals(first.key(), second.key());
    }

    @Test
    public void test_nullifiedInput_andAValue_doNotShareAKey() {
        final CacheData.SimpleKey nullified = CacheUtils.fullKeyToSimpleKey(keyOf(null));
        final CacheData.SimpleKey valued = CacheUtils.fullKeyToSimpleKey(keyOf("claude-opus-4-8"));

        assertNotNull(nullified);
        assertNotNull(valued);
        assertNotEquals(nullified.key(), valued.key());
    }

    /**
     * The production key over a Task with one local input and the cache on. content == null is the nullified input:
     * its variable has no blob, and every blob read throws - so a nullified input must be keyed without any read.
     */
    private static CacheData.FullKey keyOf(@Nullable String content) {
        final FunctionConfigYaml descriptor = new FunctionConfigYaml();
        descriptor.function.code = CODE;
        descriptor.function.sourcing = EnumsApi.FunctionSourcing.git;
        descriptor.function.git = new GitInfo("https://example.invalid/fn.git", "master", "HEAD", "fn");

        final TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.function = TaskParamsUtils.toFunctionConfig(descriptor);
        final TaskParamsYaml.InputVariable input = new TaskParamsYaml.InputVariable();
        input.id = INPUT_VARIABLE_ID;
        input.context = EnumsApi.VariableContext.local;
        input.name = "model";
        tpy.task.inputs.add(input);
        tpy.task.cache = new TaskParamsYaml.Cache(true, false, false);

        final Map<Long, byte[]> blobs = content == null
                ? Map.of()
                : Map.of(INPUT_BLOB_ID, content.getBytes(StandardCharsets.UTF_8));

        return CacheUtils.getKey(tpy, null,
                variableId -> variableId == INPUT_VARIABLE_ID && content != null ? INPUT_BLOB_ID : null,
                blobId -> { throw new IllegalStateException("this Task has no array input, blob #" + blobId); },
                blobId -> {
                    final byte[] bytes = blobs.get(blobId);
                    if (bytes == null) {
                        throw new IllegalStateException("no such blob #" + blobId);
                    }
                    return new ByteArrayInputStream(bytes);
                },
                variableId -> { throw new IllegalStateException("this Task has no global input, variable #" + variableId); });
    }
}
