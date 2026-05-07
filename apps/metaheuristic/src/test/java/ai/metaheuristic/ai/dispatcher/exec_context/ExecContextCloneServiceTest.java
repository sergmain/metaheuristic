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
package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 02 — ExecContextCloneService integration test.
 *
 * Seeds a SourceCode, creates an ExecContext via the standard test path,
 * then clones it with the new service and asserts that the clone:
 *   - is a different ExecContext row;
 *   - has its own graph / task-state / variable-state row pointers;
 *   - ends in state=FINISHED (clone went through CLONING -> FINISHED).
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
public class ExecContextCloneServiceTest extends PreparingSourceCode {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath()
                + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @SneakyThrows
    @Override
    public String getSourceCodeYamlAsString() {
        return IOUtils.resourceToString(
                "/source_code/yaml/default-source-code-for-batch-testing.yaml",
                StandardCharsets.UTF_8);
    }

    @Autowired ExecContextCloneService cloneService;
    @Autowired ExecContextRepository execContextRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired VariableRepository variableRepository;
    @Autowired TxSupportForTestingService txSupport;

    @Test
    void test_clone_minimalFreshExecContext_producesFinishedClone() {
        // arrange — create a fresh ExecContext via the standard path
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        Long sourceId = getExecContextForTest().id;

        // act — clone
        ExecContextCloneService.CloneResult result =
                cloneService.cloneExecContext(sourceId);

        // assert — clone exists, distinct id, FINISHED, distinct child pointers
        assertThat(result).isNotNull();
        assertThat(result.clonedExecContextId()).isNotEqualTo(sourceId);

        ExecContextImpl clone = execContextRepository
                .findByIdNullable(result.clonedExecContextId());
        assertThat(clone).isNotNull();
        assertThat(clone.state).isEqualTo(EnumsApi.ExecContextState.FINISHED.code);

        ExecContextImpl source = execContextRepository.findByIdNullable(sourceId);
        assertThat(clone.execContextGraphId).isNotEqualTo(source.execContextGraphId);
        assertThat(clone.execContextTaskStateId).isNotEqualTo(source.execContextTaskStateId);
        assertThat(clone.execContextVariableStateId).isNotEqualTo(source.execContextVariableStateId);

        // identity-bearing fields preserved
        assertThat(clone.sourceCodeId).isEqualTo(source.sourceCodeId);
        assertThat(clone.companyId).isEqualTo(source.companyId);
        assertThat(clone.accountId).isEqualTo(source.accountId);
    }

    @Test
    void test_clone_allTasks_allVariables_rowCountsMatch() {
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        // Produce tasks so the clone has something non-trivial to copy.
        step_0_0_produceTasks();
        Long sourceId = getExecContextForTest().id;

        long sourceTaskCount = taskRepository.findByExecContextIdReadOnly(sourceId).size();
        assertThat(sourceTaskCount).isGreaterThan(0);

        //act
        ExecContextCloneService.CloneResult result = cloneService.cloneExecContext(sourceId);

        // assert — identical Task row count between source and clone
        long clonedTaskCount = taskRepository.findByExecContextIdReadOnly(result.clonedExecContextId()).size();
        assertThat(clonedTaskCount).isEqualTo(sourceTaskCount);
        assertThat(result.taskCount()).isEqualTo(sourceTaskCount);
    }

    @Test
    void test_clone_filteredTasks_onlyAllowListedTasksCloned() {
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        step_0_0_produceTasks();
        Long sourceId = getExecContextForTest().id;

        List<TaskImpl> allSource = taskRepository.findByExecContextIdReadOnly(sourceId);
        // pick the first task as the allow-list (non-trivial — at least one)
        assertThat(allSource).isNotEmpty();
        Set<Long> allowList = Set.of(allSource.get(0).id);

        ExecContextCloneService.CloneOptions options =
                new ExecContextCloneService.CloneOptions(4, allowList);

        //act
        ExecContextCloneService.CloneResult result = cloneService.cloneExecContext(sourceId, options);

        long cloned = taskRepository.findByExecContextIdReadOnly(result.clonedExecContextId()).size();
        assertThat(cloned).isEqualTo(allowList.size());
        assertThat(result.taskCount()).isEqualTo(allowList.size());
    }

    @Test
    void test_clone_variableBlobs_areShared_notCopied() {
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        Long sourceId = getExecContextForTest().id;

        //act
        ExecContextCloneService.CloneResult result = cloneService.cloneExecContext(sourceId);

        // The clone keeps the same VARIABLE_BLOB_ID values as the source — blobs are
        // shared, not copied. Compare the multi-set of blob ids on both sides.
        Set<Long> sourceBlobs = variablesByExecContext(sourceId).stream()
                .map(v -> v.variableBlobId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> clonedBlobs = variablesByExecContext(result.clonedExecContextId()).stream()
                .map(v -> v.variableBlobId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        // Every blob id present on source must also be present on clone (sharing).
        assertThat(clonedBlobs).containsAll(sourceBlobs);
    }

    @Autowired
    ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository ecvsRepo;

    /**
     * Phase 13.G.5 — Characterization test for the parent-task-id rewrite bug
     * in {@link ExecContextCloneService#cloneExecContext}.
     *
     * <p>The clone preserves {@code TaskParamsYaml.task.init.parentTaskIds}
     * verbatim from the source EC's task rows, but those IDs reference SOURCE-EC
     * task rows. After the clone, downstream code that looks up a parent task in
     * the CLONED EC's graph (e.g. {@link ai.metaheuristic.ai.dispatcher.task.TaskVariableInitTxService}
     * via {@code getAllParentTaskContextIds} → {@code findVertexByTaskId}) fails
     * with {@code 179.240 vertex wasn't found for task #X} because the source-EC
     * parent ID does not appear in the clone's graph (the graph rewriter properly
     * replaced it with the clone-EC ID).
     *
     * <p>Test asserts: for every cloned task that has parentTaskIds, every parent
     * id present in {@code TaskParamsYaml.task.init.parentTaskIds} must be a
     * CLONE-EC task id (i.e. exists in {@code TaskRepository.findByExecContextId}
     * for the cloned EC), not a source-EC task id.
     *
     * <p>Green-1 (with fix): all parentTaskIds resolve to clone-EC tasks. Without
     * the fix, the assertion fails because parentTaskIds still point at source-EC
     * task rows.
     */
    @Test
    void test_clone_taskParentIds_rewrittenToCloneSpace() {
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        step_0_0_produceTasks();
        Long sourceId = getExecContextForTest().id;

        // Confirm at least one source task has non-empty parentTaskIds — otherwise
        // the bug is unobservable in this fixture and we should fail loudly so a
        // future maintainer doesn't think the test passed by giving zero data.
        List<TaskImpl> sourceTasks = taskRepository.findByExecContextIdReadOnly(sourceId);
        assertThat(sourceTasks).isNotEmpty();
        long sourceTasksWithParents = sourceTasks.stream()
                .map(TaskImpl::getTaskParamsYaml)
                .filter(tpy -> tpy.task != null && tpy.task.init != null
                        && tpy.task.init.parentTaskIds != null
                        && !tpy.task.init.parentTaskIds.isEmpty())
                .count();
        assertThat(sourceTasksWithParents)
                .as("fixture must have at least one task with parentTaskIds for this test to be meaningful")
                .isGreaterThan(0);

        Set<Long> sourceTaskIdSet = sourceTasks.stream()
                .map(t -> t.id)
                .collect(Collectors.toSet());

        // act — clone
        ExecContextCloneService.CloneResult result = cloneService.cloneExecContext(sourceId);
        Long cloneId = result.clonedExecContextId();
        assertThat(cloneId).isNotEqualTo(sourceId);

        // gather cloned tasks; build set of CLONE-EC task ids for membership checks
        List<TaskImpl> clonedTasks = taskRepository.findByExecContextIdReadOnly(cloneId);
        Set<Long> cloneTaskIdSet = clonedTasks.stream()
                .map(t -> t.id)
                .collect(Collectors.toSet());
        assertThat(cloneTaskIdSet).hasSize(sourceTasks.size());

        // assert — every parentTaskId on every cloned task must be a clone-EC task id,
        // never a source-EC task id. Builds full diagnostic on failure.
        java.util.List<String> violations = new java.util.ArrayList<>();
        for (TaskImpl ct : clonedTasks) {
            ai.metaheuristic.commons.yaml.task.TaskParamsYaml tpy = ct.getTaskParamsYaml();
            if (tpy.task == null || tpy.task.init == null || tpy.task.init.parentTaskIds == null) {
                continue;
            }
            for (Long pid : tpy.task.init.parentTaskIds) {
                boolean isCloneSpace = cloneTaskIdSet.contains(pid);
                boolean isSourceSpace = sourceTaskIdSet.contains(pid);
                if (!isCloneSpace) {
                    violations.add("clonedTask=#" + ct.id + " parentTaskId=" + pid
                            + (isSourceSpace ? " [SOURCE-EC ID — bug: rewrite missing]"
                                             : " [unknown — neither clone nor source EC]"));
                }
            }
        }
        assertThat(violations)
                .as("every parentTaskId on every cloned task must be remapped to clone-EC space; " +
                    "violations indicate ExecContextCloneTxService.insertNewTask did not rewrite " +
                    "TaskParamsYaml.task.init.parentTaskIds via taskIdMap")
                .isEmpty();
    }

    private List<Variable> variablesByExecContext(Long execContextId) {
        // pull the variable IDs the same way the service does — straight from the
        // ExecContextVariableState content — then load each Variable row.
        ExecContextImpl ec = execContextRepository.findByIdNullable(execContextId);
        if (ec == null) {
            return List.of();
        }
        String varStateJson = ecvsRepo.findById(ec.execContextVariableStateId)
                .map(s -> s.getParams())
                .orElse("");
        Set<Long> ids = ExecContextCloneService.collectVariableIds(varStateJson);
        if (ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(id -> variableRepository.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
