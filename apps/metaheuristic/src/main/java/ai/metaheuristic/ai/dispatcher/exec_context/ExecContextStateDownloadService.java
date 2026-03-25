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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextGraphRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Serge
 * Date: 3/25/2026
 */
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextStateDownloadService {

    private final ExecContextRepository execContextRepository;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextVariableStateRepository execContextVariableStateRepository;

    @Transactional(readOnly = true)
    public CleanerInfo downloadExecContextStates(Long execContextId) {
        CleanerInfo resource = new CleanerInfo();
        try {
            ExecContextImpl execContext = execContextRepository.findByIdNullable(execContextId);
            if (execContext == null) {
                resource.addErrorMessage("458.020 ExecContext #" + execContextId + " not found");
                return resource;
            }

            Path tempDir = DirUtils.createMhTempPath("exec-context-states-");
            if (tempDir == null) {
                resource.addErrorMessage("458.040 Can't create temporary dir");
                return resource;
            }
            resource.toClean.add(tempDir);

            Path filesDir = tempDir.resolve("files");
            Files.createDirectories(filesDir);

            // 1) ExecContextTaskState params
            writeEntityParams(execContext.execContextTaskStateId, "exec-context-task-state.yaml",
                    filesDir, resource, execContextTaskStateRepository);

            // 2) ExecContextGraph params
            writeEntityParams(execContext.execContextGraphId, "exec-context-graph.yaml",
                    filesDir, resource, execContextGraphRepository);

            // 3) ExecContextVariableState params
            writeEntityParams(execContext.execContextVariableStateId, "exec-context-variable-state.yaml",
                    filesDir, resource, execContextVariableStateRepository);

            // 4) Process DAG (only processesGraph from ExecContext.params)
            String processesGraph = execContext.getExecContextParamsYaml().processesGraph;
            if (processesGraph != null) {
                Files.writeString(filesDir.resolve("exec-context-process-dag.yaml"), processesGraph);
            }

            Path zipFile = tempDir.resolve("exec-context-states-" + execContextId + ".zip");
            ZipUtils.createZip(filesDir, zipFile);

            final HttpHeaders headers = RestUtils.getHeader(null, Files.size(zipFile));
            resource.entity = new ResponseEntity<>(new FileSystemResource(zipFile), headers, HttpStatus.OK);
            return resource;
        }
        catch (IOException e) {
            log.error("458.060 Error creating zip for execContext states", e);
            resource.addErrorMessage("458.060 Error: " + e.getMessage());
            return resource;
        }
    }

    private void writeEntityParams(@Nullable Long entityId, String fileName, Path filesDir,
                                   CleanerInfo resource,
                                   Object repository) throws IOException {
        if (entityId == null) {
            log.warn("458.080 Entity id is null for {}", fileName);
            return;
        }
        String params = getParamsFromRepository(entityId, repository);
        if (params == null) {
            log.warn("458.100 Entity #{} not found for {}", entityId, fileName);
            return;
        }
        Files.writeString(filesDir.resolve(fileName), params);
    }

    private static @Nullable String getParamsFromRepository(Long entityId, Object repository) {
        if (repository instanceof ExecContextTaskStateRepository repo) {
            ExecContextTaskState entity = repo.findById(entityId).orElse(null);
            return entity != null ? entity.getParams() : null;
        }
        if (repository instanceof ExecContextGraphRepository repo) {
            ExecContextGraph entity = repo.findById(entityId).orElse(null);
            return entity != null ? entity.getParams() : null;
        }
        if (repository instanceof ExecContextVariableStateRepository repo) {
            ExecContextVariableState entity = repo.findById(entityId).orElse(null);
            return entity != null ? entity.getParams() : null;
        }
        return null;
    }
}
