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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.processor_environment.MetadataParams;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
class FinishedExecContextsServiceTest {

    private static final DispatcherUrl DISPATCHER_A = new DispatcherUrl("http://localhost:8080");
    private static final DispatcherUrl DISPATCHER_B = new DispatcherUrl("https://dispatcher-b.example:443");

    // ---- static, Path-based ----

    @Test
    public void test_file_isInProcessorDir_onePerDispatcher(@TempDir Path processorPath) {
        final Path a = FinishedExecContextsService.file(processorPath, DISPATCHER_A);
        final Path b = FinishedExecContextsService.file(processorPath, DISPATCHER_B);

        assertEquals(processorPath, a.getParent(), "the file is in the processor dir, beside env.yaml and metadata.yaml");
        assertEquals("finished-exec-contexts-" + MetadataParams.asCode(DISPATCHER_A) + ".json", a.getFileName().toString());
        assertNotEquals(a, b, "an ExecContext id is unique only within its Dispatcher - one file per Dispatcher");
    }

    @Test
    public void test_read_absentFile_isNoIds(@TempDir Path dir) {
        assertEquals(Set.of(), FinishedExecContextsService.read(dir.resolve("absent.json")));
    }

    @Test
    public void test_read_brokenFile_isNoIds(@TempDir Path dir) throws IOException {
        final Path file = Files.writeString(dir.resolve("broken.json"), "{\"ids\":[1,");
        assertEquals(Set.of(), FinishedExecContextsService.read(file));
    }

    @Test
    public void test_read_unknownProperty_isIgnored(@TempDir Path dir) throws IOException {
        final Path file = Files.writeString(dir.resolve("future.json"), "{\"ids\":[7,3],\"addedLater\":\"x\"}");
        assertEquals(Set.of(3L, 7L), FinishedExecContextsService.read(file));
    }

    @Test
    public void test_write_onlyIds_sorted_andReadBack(@TempDir Path dir) throws IOException {
        final Path file = dir.resolve("f.json");
        FinishedExecContextsService.write(file, Set.of(9L, 1L, 5L));

        assertEquals("{\"ids\":[1,5,9]}", Files.readString(file, StandardCharsets.UTF_8));
        assertEquals(Set.of(1L, 5L, 9L), FinishedExecContextsService.read(file));
        assertTrue(Files.notExists(dir.resolve("f.json.tmp")), "the temp file must be moved, not left behind");
    }

    @Test
    public void test_write_replacesPreviousContent(@TempDir Path dir) throws IOException {
        final Path file = dir.resolve("f.json");
        FinishedExecContextsService.write(file, Set.of(1L, 2L));
        FinishedExecContextsService.write(file, Set.of());

        assertEquals("{\"ids\":[]}", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    public void test_finishedOf_onlyFinishedState() {
        assertEquals(Set.of(1L, 3L), FinishedExecContextsService.finishedOf(Map.of(
            EnumsApi.ExecContextState.FINISHED, "3,1",
            EnumsApi.ExecContextState.STARTED, "2",
            EnumsApi.ExecContextState.ERROR, "4")));
    }

    @Test
    public void test_finishedOf_noFinished() {
        assertEquals(Set.of(), FinishedExecContextsService.finishedOf(Map.of(EnumsApi.ExecContextState.STARTED, "2")));
        assertEquals(Set.of(), FinishedExecContextsService.finishedOf(Map.of()));
    }

    @Test
    public void test_applyDelta() {
        final Set<Long> ids = new HashSet<>(Set.of(1L, 2L));

        assertEquals(Set.of(1L, 2L, 3L), FinishedExecContextsService.applyDelta(ids, 3L, EnumsApi.ExecContextState.FINISHED));
        assertEquals(Set.of(2L), FinishedExecContextsService.applyDelta(ids, 1L, EnumsApi.ExecContextState.STARTED));
        assertEquals(Set.of(1L), FinishedExecContextsService.applyDelta(ids, 2L, null));
        assertEquals(Set.of(1L, 2L), ids, "the input must not be changed");
    }

    // ---- the service over a real processor dir ----

    private static FinishedExecContextsService service(Path processorPath) {
        final Globals globals = new Globals();
        globals.processor.enabled = true;
        globals.processorPath = processorPath;
        // ProcessorEnvironment is used only by postConstruct() for loading eagerly; every file is also loaded on first use
        return new FinishedExecContextsService(globals, null);
    }

    private static String content(Path processorPath, DispatcherUrl dispatcherUrl) throws IOException {
        return Files.readString(FinishedExecContextsService.file(processorPath, dispatcherUrl), StandardCharsets.UTF_8);
    }

    @Test
    public void test_registerFull_storesFinished(@TempDir Path processorPath) throws IOException {
        final FinishedExecContextsService service = service(processorPath);

        service.registerFull(DISPATCHER_A, Map.of(
            EnumsApi.ExecContextState.FINISHED, "12,11",
            EnumsApi.ExecContextState.STARTED, "13"));

        assertEquals("{\"ids\":[11,12]}", content(processorPath, DISPATCHER_A));
        assertTrue(service.isFinished(DISPATCHER_A, 11L));
        assertFalse(service.isFinished(DISPATCHER_A, 13L), "a STARTED ExecContext isn't FINISHED");
        assertFalse(service.isFinished(DISPATCHER_A, null));
    }

    @Test
    public void test_registerFull_deletedOrRestarted_leavesTheList(@TempDir Path processorPath) throws IOException {
        final FinishedExecContextsService service = service(processorPath);
        service.registerFull(DISPATCHER_A, Map.of(EnumsApi.ExecContextState.FINISHED, "21,22,23"));

        // #21 was deleted at the Dispatcher - absent in the snapshot, #23 was started again
        service.registerFull(DISPATCHER_A, Map.of(
            EnumsApi.ExecContextState.FINISHED, "22",
            EnumsApi.ExecContextState.STARTED, "23"));

        assertEquals("{\"ids\":[22]}", content(processorPath, DISPATCHER_A));
        assertFalse(service.isFinished(DISPATCHER_A, 21L));
        assertFalse(service.isFinished(DISPATCHER_A, 23L));
    }

    @Test
    public void test_registerFull_noExecContexts_emptiesTheList(@TempDir Path processorPath) throws IOException {
        final FinishedExecContextsService service = service(processorPath);
        service.registerFull(DISPATCHER_A, Map.of(EnumsApi.ExecContextState.FINISHED, "31"));

        service.registerFull(DISPATCHER_A, Map.of());

        assertEquals("{\"ids\":[]}", content(processorPath, DISPATCHER_A));
        assertFalse(service.isFinished(DISPATCHER_A, 31L));
    }

    @Test
    public void test_registerFull_unchanged_isNotWritten(@TempDir Path processorPath) throws IOException {
        final FinishedExecContextsService service = service(processorPath);
        service.registerFull(DISPATCHER_A, Map.of(EnumsApi.ExecContextState.FINISHED, "41"));
        final Path file = FinishedExecContextsService.file(processorPath, DISPATCHER_A);
        Files.delete(file);

        service.registerFull(DISPATCHER_A, Map.of(EnumsApi.ExecContextState.FINISHED, "41"));

        assertTrue(Files.notExists(file), "the ids didn't change - the file must not be written");
    }

    @Test
    public void test_registerFull_nothingFinished_noFileYet_isNotWritten(@TempDir Path processorPath) {
        final FinishedExecContextsService service = service(processorPath);

        service.registerFull(DISPATCHER_A, Map.of(EnumsApi.ExecContextState.STARTED, "51"));

        assertTrue(Files.notExists(FinishedExecContextsService.file(processorPath, DISPATCHER_A)));
    }

    @Test
    public void test_registerDelta(@TempDir Path processorPath) throws IOException {
        final FinishedExecContextsService service = service(processorPath);

        service.registerDelta(DISPATCHER_A, 61L, EnumsApi.ExecContextState.FINISHED);
        assertEquals("{\"ids\":[61]}", content(processorPath, DISPATCHER_A));

        service.registerDelta(DISPATCHER_A, 61L, EnumsApi.ExecContextState.STARTED);
        assertEquals("{\"ids\":[]}", content(processorPath, DISPATCHER_A));
        assertFalse(service.isFinished(DISPATCHER_A, 61L));
    }

    @Test
    public void test_dispatchersAreIsolated(@TempDir Path processorPath) throws IOException {
        final FinishedExecContextsService service = service(processorPath);

        service.registerFull(DISPATCHER_A, Map.of(EnumsApi.ExecContextState.FINISHED, "71"));
        service.registerFull(DISPATCHER_B, Map.of(EnumsApi.ExecContextState.FINISHED, "72"));

        assertTrue(service.isFinished(DISPATCHER_A, 71L));
        assertFalse(service.isFinished(DISPATCHER_B, 71L), "ExecContext #71 of Dispatcher A says nothing about #71 of Dispatcher B");
        assertEquals("{\"ids\":[71]}", content(processorPath, DISPATCHER_A));
        assertEquals("{\"ids\":[72]}", content(processorPath, DISPATCHER_B));
    }

    @Test
    public void test_newInstance_readsTheStoredFile(@TempDir Path processorPath) {
        service(processorPath).registerFull(DISPATCHER_A, Map.of(EnumsApi.ExecContextState.FINISHED, "81"));

        final FinishedExecContextsService restarted = service(processorPath);

        assertTrue(restarted.isFinished(DISPATCHER_A, 81L), "a restarted Processor must know the FINISHED ExecContexts from the file");
        assertFalse(restarted.isFinished(DISPATCHER_A, 82L));
    }
}
