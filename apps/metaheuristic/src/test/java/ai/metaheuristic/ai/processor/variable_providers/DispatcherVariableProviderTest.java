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
package ai.metaheuristic.ai.processor.variable_providers;

import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.actors.UploadVariableService;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.tasks.UploadVariableTask;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DispatcherVariableProvider#processOutputVariable} against the contract between an external Function and
 * the Processor for an output Variable, which the Function leaves in the Task dir as file artifacts/&lt;variableId&gt;:
 * <ol>
 *     <li>a non-nullable output Variable - the file must exist;</li>
 *     <li>a nullable output Variable - the file may be absent, the Variable is then reported as null;</li>
 *     <li>an existing file must be longer than 0 bytes - a Function must never create a zero-byte output Variable.</li>
 * </ol>
 * A violation must fail the Task with an error naming the output Variable, and nothing is queued for uploading.
 *
 * <p>Spring-less. The provider and its {@link UploadVariableService} are the real classes - no member they don't
 * reach in this path is set. Nothing drains the upload queue here, so the test inspects the real queue as
 * production left it.
 */
@Execution(ExecutionMode.CONCURRENT)
public class DispatcherVariableProviderTest {

    private static final Long TASK_ID = 42L;
    private static final Long VARIABLE_ID = 101L;
    private static final String FUNCTION_CODE = "test.output-contract-function:1.0";

    record Env(DispatcherVariableProvider provider, UploadVariableService uploadVariableService) {}

    @SuppressWarnings("DataFlowIssue")
    static Env env() {
        // nothing in processOutputVariable() reaches the services' collaborators
        final UploadVariableService uploadVariableService = new UploadVariableService(null, null, null);
        return new Env(new DispatcherVariableProvider(null, uploadVariableService), uploadVariableService);
    }

    @SuppressWarnings("DataFlowIssue")
    static FunctionApiData.@Nullable SystemExecResult process(Env env, Path taskDir, TaskParamsYaml.OutputVariable outputVariable) {
        final ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core = new ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef(
                new ProcessorAndCoreData.DispatcherUrl("http://localhost:8080"), "localhost-8080", 17L, "core-code-1", 7L);

        final DispatcherLookupParamsYaml.DispatcherLookup lookup = new DispatcherLookupParamsYaml.DispatcherLookup();
        lookup.restUsername = "rest-user";
        lookup.restPassword = "rest-password";
        final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher =
                new DispatcherLookupExtendedParams.DispatcherLookupExtended(core.dispatcherUrl, lookup, null);

        final ProcessorCoreTask task = new ProcessorCoreTask();
        task.taskId = TASK_ID;

        final TaskParamsYaml.FunctionConfig functionConfig = new TaskParamsYaml.FunctionConfig();
        functionConfig.code = FUNCTION_CODE;

        return env.provider.processOutputVariable(core, taskDir, dispatcher, task, null, outputVariable, functionConfig);
    }

    static TaskParamsYaml.OutputVariable output(Long id, boolean nullable) {
        final TaskParamsYaml.OutputVariable o = new TaskParamsYaml.OutputVariable();
        o.id = id;
        o.context = EnumsApi.VariableContext.local;
        o.name = "var-" + id;
        o.sourcing = EnumsApi.DataSourcing.dispatcher;
        o.setNullable(nullable);
        return o;
    }

    static Path writeArtifact(Path taskDir, Long variableId, String content) throws IOException {
        final Path artifactsDir = Files.createDirectories(taskDir.resolve(ConstsApi.ARTIFACTS_DIR));
        return Files.writeString(artifactsDir.resolve(variableId.toString()), content, StandardCharsets.UTF_8);
    }

    // rule 3 - an existing file must be longer than 0 bytes

    @Test
    public void test_zeroByteFile_notNullable(@TempDir Path taskDir) throws IOException {
        final Env env = env();
        final Path file = writeArtifact(taskDir, VARIABLE_ID, "");

        final FunctionApiData.SystemExecResult result = process(env, taskDir, output(VARIABLE_ID, false));

        // a zero-byte file fails the Task, the error names output Variable #101, nothing is uploaded
        assertNotNull(result, "zero-byte, not nullable: the provider reports an error");
        assertFalse(result.isOk);
        assertEquals(FUNCTION_CODE, result.functionCode);
        assertTrue(result.console.contains("output Variable #" + VARIABLE_ID + " violates contract"), "zero-byte, not nullable: " + result.console);
        assertTrue(result.console.contains(file.toAbsolutePath().toString()), "zero-byte, not nullable: " + result.console);
        assertEquals(0, env.uploadVariableService.queueSize(), "zero-byte, not nullable: nothing is queued");
    }

    @Test
    public void test_zeroByteFile_nullable(@TempDir Path taskDir) throws IOException {
        final Env env = env();
        final Path file = writeArtifact(taskDir, VARIABLE_ID, "");

        final FunctionApiData.SystemExecResult result = process(env, taskDir, output(VARIABLE_ID, true));

        // nullable doesn't admit a zero-byte file: a nullable Variable is null by an absent file, not by an empty one
        assertNotNull(result, "zero-byte, nullable: the provider reports an error");
        assertFalse(result.isOk);
        assertEquals(FUNCTION_CODE, result.functionCode);
        assertTrue(result.console.contains("output Variable #" + VARIABLE_ID + " violates contract"), "zero-byte, nullable: " + result.console);
        assertTrue(result.console.contains(file.toAbsolutePath().toString()), "zero-byte, nullable: " + result.console);
        assertEquals(0, env.uploadVariableService.queueSize(), "zero-byte, nullable: nothing is queued");
    }

    // rule 1 - a non-nullable output Variable must have its file

    @Test
    public void test_absentFile_notNullable(@TempDir Path taskDir) {
        final Env env = env();

        final FunctionApiData.SystemExecResult result = process(env, taskDir, output(VARIABLE_ID, false));

        // the Task fails, and the error says that the Function broke the contract of output Variable #101
        assertNotNull(result, "absent, not nullable: the provider reports an error");
        assertFalse(result.isOk);
        assertEquals(FUNCTION_CODE, result.functionCode);
        assertTrue(result.console.contains("output Variable #" + VARIABLE_ID + " violates contract"), "absent, not nullable: " + result.console);
        assertEquals(0, env.uploadVariableService.queueSize(), "absent, not nullable: nothing is queued");
    }

    // rule 2 - a nullable output Variable may be left without a file

    @Test
    public void test_absentFile_nullable(@TempDir Path taskDir) {
        final Env env = env();

        final FunctionApiData.SystemExecResult result = process(env, taskDir, output(VARIABLE_ID, true));

        assertNull(result, "absent, nullable: the provider reports no error");
        assertEquals(1, env.uploadVariableService.queueSize(), "absent, nullable: one upload is queued");
        final UploadVariableTask uploadTask = env.uploadVariableService.poll();
        assertNotNull(uploadTask);
        assertTrue(uploadTask.nullified, "absent, nullable: the Variable is reported as null");
        assertNull(uploadTask.file);
        assertEquals(VARIABLE_ID, uploadTask.variableId);
        assertEquals(TASK_ID, uploadTask.taskId);
    }

    // a file with content satisfies the contract, nullable or not

    @Test
    public void test_nonEmptyFile_notNullable(@TempDir Path taskDir) throws IOException {
        final Env env = env();
        final Path file = writeArtifact(taskDir, VARIABLE_ID, "the report");

        final FunctionApiData.SystemExecResult result = process(env, taskDir, output(VARIABLE_ID, false));

        assertNull(result, "non-empty, not nullable: the provider reports no error");
        assertEquals(1, env.uploadVariableService.queueSize(), "non-empty, not nullable: one upload is queued");
        final UploadVariableTask uploadTask = env.uploadVariableService.poll();
        assertNotNull(uploadTask);
        assertEquals(file, uploadTask.file);
        assertFalse(uploadTask.nullified);
        assertEquals(VARIABLE_ID, uploadTask.variableId);
    }

    @Test
    public void test_nonEmptyFile_nullable(@TempDir Path taskDir) throws IOException {
        final Env env = env();
        final Path file = writeArtifact(taskDir, VARIABLE_ID, "the report");

        final FunctionApiData.SystemExecResult result = process(env, taskDir, output(VARIABLE_ID, true));

        assertNull(result, "non-empty, nullable: the provider reports no error");
        assertEquals(1, env.uploadVariableService.queueSize(), "non-empty, nullable: one upload is queued");
        final UploadVariableTask uploadTask = env.uploadVariableService.poll();
        assertNotNull(uploadTask);
        assertEquals(file, uploadTask.file);
        assertFalse(uploadTask.nullified);
    }
}
