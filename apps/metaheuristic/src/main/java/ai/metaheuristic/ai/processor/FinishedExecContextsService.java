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
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.JsonUtils;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;

/**
 * The ids of FINISHED ExecContexts of each Dispatcher, as the Dispatcher reports them, persisted in
 * {@code {processorDir}/finished-exec-contexts-{dispatcherCode}.json} - the dir of env.yaml and metadata.yaml.
 * One file per Dispatcher, because an ExecContext id is unique only within its Dispatcher. Content: {@code {"ids":[...]}}.
 *
 * <ul>
 *     <li>a full snapshot of states (keep-alive) replaces the ids with the FINISHED ones of the snapshot - an ExecContext
 *     deleted at the Dispatcher is absent in the snapshot, so it leaves the list, as does one which isn't FINISHED any more;</li>
 *     <li>a delta (an assigned Task) adds its ExecContext when it's FINISHED and removes it otherwise;</li>
 *     <li>a file is written only when its ids changed.</li>
 * </ul>
 * The files are read in {@link #postConstruct()}, i.e. before ProcessorTaskService.init(), which depends on this service
 * and doesn't load a Task of a FINISHED ExecContext.
 * Static methods are Path-based on purpose - they're testable without a Spring context.
 *
 * <p>Error code prefix: {@code 01.716.} (unique to this class).
 */
@Slf4j
@Service
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FinishedExecContextsService {

    public static final String FILE_NAME_MASK = "finished-exec-contexts-%s.json";

    /** The content of the file. Unknown properties are ignored on reading, so fields can be added later. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinishedExecContexts {
        public List<Long> ids = new ArrayList<>();
    }

    private final Globals globals;
    private final ProcessorEnvironment processorEnvironment;

    /** key - Dispatcher, value - ids of its FINISHED ExecContexts, as they are in its file */
    private final ConcurrentHashMap<DispatcherUrl, Set<Long>> finished = new ConcurrentHashMap<>();

    @PostConstruct
    public void postConstruct() {
        if (globals.testing || !globals.processor.enabled) {
            return;
        }
        processorEnvironment.getProcessorEnv().metadataParams().getAllEnabledRefsForCores().stream()
                .map(core -> core.dispatcherUrl)
                .distinct()
                .forEach(this::getFinished);
    }

    private Set<Long> getFinished(DispatcherUrl dispatcherUrl) {
        return finished.computeIfAbsent(dispatcherUrl, url -> read(file(globals.processorPath, url)));
    }

    public boolean isFinished(DispatcherUrl dispatcherUrl, @Nullable Long execContextId) {
        return execContextId != null && getFinished(dispatcherUrl).contains(execContextId);
    }

    /**
     * @param statuses a full snapshot of states of all ExecContexts of the Dispatcher: key - state, value - comma-separated ids
     */
    public synchronized void registerFull(DispatcherUrl dispatcherUrl, Map<EnumsApi.ExecContextState, String> statuses) {
        update(dispatcherUrl, finishedOf(statuses));
    }

    public synchronized void registerDelta(DispatcherUrl dispatcherUrl, Long execContextId, EnumsApi.@Nullable ExecContextState state) {
        update(dispatcherUrl, applyDelta(getFinished(dispatcherUrl), execContextId, state));
    }

    private void update(DispatcherUrl dispatcherUrl, Set<Long> ids) {
        if (ids.equals(getFinished(dispatcherUrl))) {
            return;
        }
        final Path file = file(globals.processorPath, dispatcherUrl);
        try {
            write(file, ids);
            // only after a successful write - a failed one is retried with the next registration
            finished.put(dispatcherUrl, ids);
        } catch (Throwable th) {
            log.error("01.716.020 Error writing the file of FINISHED ExecContexts " + file.toAbsolutePath(), th);
        }
    }

    public static Path file(Path processorPath, ProcessorAndCoreData.CommonUrl dispatcherUrl) {
        return processorPath.resolve(String.format(FILE_NAME_MASK, MetadataParams.asCode(dispatcherUrl)));
    }

    /**
     * @return the ids in the file. An absent or unreadable file means no ids, i.e. no Task is filtered out because of it.
     */
    public static Set<Long> read(Path file) {
        if (Files.notExists(file)) {
            return Set.of();
        }
        try {
            final FinishedExecContexts fec = JsonUtils.getMapper().readValue(Files.readString(file, StandardCharsets.UTF_8), FinishedExecContexts.class);
            return fec.ids == null ? Set.of() : fec.ids.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
        } catch (Throwable th) {
            log.warn("01.716.010 The file of FINISHED ExecContexts {} can't be read, no Task will be filtered out because of it, error: {}",
                    file.toAbsolutePath(), th.toString());
            return Set.of();
        }
    }

    /** Writes the ids, sorted, via a temp file and an atomic move - a crash never leaves a partly written file. */
    public static void write(Path file, Set<Long> ids) throws IOException {
        final String json = JsonUtils.getMapper().writeValueAsString(new FinishedExecContexts(ids.stream().sorted().toList()));
        final Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * @param statuses a full snapshot of states: key - state, value - comma-separated ids
     * @return the ids of the FINISHED ExecContexts in the snapshot
     */
    public static Set<Long> finishedOf(Map<EnumsApi.ExecContextState, String> statuses) {
        final String ids = statuses.get(EnumsApi.ExecContextState.FINISHED);
        if (S.b(ids)) {
            return Set.of();
        }
        return Arrays.stream(ids.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** @return the ids with execContextId added when its state is FINISHED, and removed otherwise */
    public static Set<Long> applyDelta(Set<Long> ids, Long execContextId, EnumsApi.@Nullable ExecContextState state) {
        final Set<Long> result = new HashSet<>(ids);
        if (state == EnumsApi.ExecContextState.FINISHED) {
            result.add(execContextId);
        }
        else {
            result.remove(execContextId);
        }
        return Set.copyOf(result);
    }
}
