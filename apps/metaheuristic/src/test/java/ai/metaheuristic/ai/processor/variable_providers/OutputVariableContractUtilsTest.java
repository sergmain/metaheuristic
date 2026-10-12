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

import ai.metaheuristic.ai.processor.tasks.UploadVariableTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static ai.metaheuristic.ai.processor.variable_providers.DispatcherVariableProviderTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OutputVariableContractUtils}: the contract check against real files in a Task dir, and the processing of
 * several output Variables, which must not lose a violation. The processing tests pass the real
 * {@link DispatcherVariableProvider} - the same thing production passes - and inspect its real upload queue.
 */
@Execution(ExecutionMode.CONCURRENT)
public class OutputVariableContractUtilsTest {

    private static final Long VAR_1 = 101L;
    private static final Long VAR_2 = 102L;

    private static Path artifact(Path taskDir, Long variableId) {
        return taskDir.resolve(ConstsApi.ARTIFACTS_DIR).resolve(variableId.toString());
    }

    private static void assertViolation(Long variableId, Path file, @org.jspecify.annotations.Nullable String violation, String phase) {
        assertNotNull(violation, phase + ": a violation is reported");
        assertTrue(violation.contains("output Variable #" + variableId + " violates contract"), phase + ": " + violation);
        assertTrue(violation.contains(file.toAbsolutePath().toString()), phase + ": " + violation);
    }

    // findViolation() - rule 1, a non-nullable output Variable must have its file

    @Test
    public void test_findViolation_absentFile_notNullable(@TempDir Path taskDir) {
        final Path file = artifact(taskDir, VAR_1);
        assertViolation(VAR_1, file, OutputVariableContractUtils.findViolation(file, output(VAR_1, false)), "absent, not nullable");
    }

    @Test
    public void test_findViolation_absentFile_nullableUnset(@TempDir Path taskDir) {
        final Path file = artifact(taskDir, VAR_1);
        final TaskParamsYaml.OutputVariable outputVariable = output(VAR_1, false);
        outputVariable.setNullable(null);
        assertViolation(VAR_1, file, OutputVariableContractUtils.findViolation(file, outputVariable), "absent, nullable unset means not nullable");
    }

    // findViolation() - rule 2, a nullable output Variable may be left without a file

    @Test
    public void test_findViolation_absentFile_nullable(@TempDir Path taskDir) {
        final Path file = artifact(taskDir, VAR_1);
        assertNull(OutputVariableContractUtils.findViolation(file, output(VAR_1, true)), "absent, nullable: no violation");
    }

    // findViolation() - rule 3, an existing file must be longer than 0 bytes

    @Test
    public void test_findViolation_zeroByteFile_notNullable(@TempDir Path taskDir) throws IOException {
        final Path file = writeArtifact(taskDir, VAR_1, "");
        assertViolation(VAR_1, file, OutputVariableContractUtils.findViolation(file, output(VAR_1, false)), "zero-byte, not nullable");
    }

    @Test
    public void test_findViolation_zeroByteFile_nullable(@TempDir Path taskDir) throws IOException {
        final Path file = writeArtifact(taskDir, VAR_1, "");
        assertViolation(VAR_1, file, OutputVariableContractUtils.findViolation(file, output(VAR_1, true)), "zero-byte, nullable");
    }

    @Test
    public void test_findViolation_oneByteFile_notNullable(@TempDir Path taskDir) throws IOException {
        final Path file = writeArtifact(taskDir, VAR_1, "1");
        assertNull(OutputVariableContractUtils.findViolation(file, output(VAR_1, false)), "one byte, not nullable: no violation");
    }

    @Test
    public void test_findViolation_oneByteFile_nullable(@TempDir Path taskDir) throws IOException {
        final Path file = writeArtifact(taskDir, VAR_1, "1");
        assertNull(OutputVariableContractUtils.findViolation(file, output(VAR_1, true)), "one byte, nullable: no violation");
    }

    // processOutputVariables() - a violation must survive the output Variables after it

    @Test
    public void test_processOutputVariables_violationOfFirst_isNotOverwrittenByValidSecond(@TempDir Path taskDir) throws IOException {
        final Env env = env();
        final Path file1 = writeArtifact(taskDir, VAR_1, "");
        writeArtifact(taskDir, VAR_2, "the second report");

        final FunctionApiData.SystemExecResult result = OutputVariableContractUtils.processOutputVariables(
                List.of(output(VAR_1, false), output(VAR_2, false)), o -> process(env, taskDir, o));

        assertNotNull(result, "violation of #101, then valid #102: the Task fails");
        assertFalse(result.isOk);
        assertViolation(VAR_1, file1, result.console, "violation of #101, then valid #102");
        assertEquals(0, env.uploadVariableService().queueSize(), "violation of #101, then valid #102: #102 isn't uploaded for a failed Task");
    }

    @Test
    public void test_processOutputVariables_violationOfLast(@TempDir Path taskDir) throws IOException {
        final Env env = env();
        final Path file1 = writeArtifact(taskDir, VAR_1, "the first report");
        final Path file2 = writeArtifact(taskDir, VAR_2, "");

        final FunctionApiData.SystemExecResult result = OutputVariableContractUtils.processOutputVariables(
                List.of(output(VAR_1, false), output(VAR_2, false)), o -> process(env, taskDir, o));

        assertNotNull(result, "valid #101, then violation of #102: the Task fails");
        assertFalse(result.isOk);
        assertViolation(VAR_2, file2, result.console, "valid #101, then violation of #102");
        // #101 was processed before #102 was reached
        assertEquals(1, env.uploadVariableService().queueSize(), "valid #101, then violation of #102: #101 is already queued");
        final UploadVariableTask uploadTask = env.uploadVariableService().poll();
        assertNotNull(uploadTask);
        assertEquals(file1, uploadTask.file);
    }

    @Test
    public void test_processOutputVariables_allValid(@TempDir Path taskDir) throws IOException {
        final Env env = env();
        writeArtifact(taskDir, VAR_1, "the first report");

        final FunctionApiData.SystemExecResult result = OutputVariableContractUtils.processOutputVariables(
                List.of(output(VAR_1, false), output(VAR_2, true)), o -> process(env, taskDir, o));

        assertNull(result, "valid #101, absent nullable #102: no error");
        assertEquals(2, env.uploadVariableService().queueSize(), "valid #101, absent nullable #102: both are queued");
        final UploadVariableTask uploadTask1 = env.uploadVariableService().poll();
        final UploadVariableTask uploadTask2 = env.uploadVariableService().poll();
        assertNotNull(uploadTask1);
        assertNotNull(uploadTask2);
        assertEquals(VAR_1, uploadTask1.variableId);
        assertFalse(uploadTask1.nullified);
        assertEquals(VAR_2, uploadTask2.variableId);
        assertTrue(uploadTask2.nullified, "absent nullable #102 is reported as null");
    }

    @Test
    public void test_processOutputVariables_noOutputs(@TempDir Path taskDir) {
        final Env env = env();

        final FunctionApiData.SystemExecResult result = OutputVariableContractUtils.processOutputVariables(
                List.of(), o -> process(env, taskDir, o));

        assertNull(result, "no output Variables: no error");
        assertEquals(0, env.uploadVariableService().queueSize());
    }
}
