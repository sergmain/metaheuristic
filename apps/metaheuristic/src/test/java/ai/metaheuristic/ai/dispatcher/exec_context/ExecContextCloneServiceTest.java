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
    @Autowired ExecContextCloneTxService cloneTxService;

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


    /**
     * MINIMAL ISOLATION TEST for the puzzle: pre-allocate a TaskImpl row in
     * REQUIRES_NEW TX, then immediately try to read it back via another
     * REQUIRES_NEW TX call on the same TX-service. Both calls go through the
     * Spring proxy so the TX boundaries are honored. If the row is invisible
     * to the second call, this test fails — and we have a reliable repro.
     *
     * Stripping every other moving part: no graph, no variables, no full clone,
     * no orchestrator, no ExecContextSyncService nesting. Just two REQUIRES_NEW
     * boundaries on the same bean.
     */
    @Test
    void test_minimalRepro_preAllocateThenReadInAnotherTx() {
        // arrange — need a parent ExecContext row so the FK on TaskImpl.execContextId is valid
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        Long ecId = creation.execContext.id;

        // act — pre-allocate
        Long allocatedId = cloneTxService.preAllocateClonedTask(ecId);
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("MINREPRO: preAllocate returned id={}", allocatedId);

        // act — read from a NEW TX boundary (same as fillClonedTask would)
        TaskImpl viaFindById = cloneTxService.debugFindById(allocatedId);
        TaskImpl viaFindByIdReadOnly = cloneTxService.debugFindByIdReadOnly(allocatedId);
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("MINREPRO: findById={}, findByIdReadOnly={}",
                        viaFindById != null, viaFindByIdReadOnly != null);

        // assert — both read paths must see the row
        assertThat(viaFindByIdReadOnly)
                .as("findByIdReadOnly (JPQL) must see the just-inserted row (id=" + allocatedId + ")")
                .isNotNull();
        assertThat(viaFindById)
                .as("findById (CrudRepository) must see the just-inserted row (id=" + allocatedId + ")")
                .isNotNull();
    }


    /**
     * SECOND-LEVEL REPRO — mirrors the actual production loop in
     * cloneExecContext: pre-allocate N rows in pass A, then for each one
     * read+update via fillClonedTask in pass B. Uses real source tasks
     * with valid TaskParamsYaml (via step_0_0_produceTasks).
     */
    @Test
    void test_minimalRepro_twoPassLoop_fillClonedTaskFindsAllRows() {
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        // produce real tasks with real TaskParamsYaml
        step_0_0_produceTasks();
        Long sourceEcId = getExecContextForTest().id;

        java.util.List<TaskImpl> sourceTasks = taskRepository.findByExecContextIdReadOnly(sourceEcId);
        assertThat(sourceTasks).as("source tasks produced").isNotEmpty();
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("MINREPRO2: source ec={} has {} tasks", sourceEcId, sourceTasks.size());

        // need a separate ec id for the "clone" target. We just reuse the same
        // sourceEcId here since we only care about whether pre-allocate + fill
        // is observable; FK references the same EC row.
        Long targetEcId = sourceEcId;

        // pass A — pre-allocate one row per source task
        java.util.Map<Long, Long> taskIdMap = new java.util.HashMap<>();
        for (TaskImpl src : sourceTasks) {
            Long clonedId = cloneTxService.preAllocateClonedTask(targetEcId);
            taskIdMap.put(src.id, clonedId);
        }
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("MINREPRO2: taskIdMap={}", taskIdMap);

        // pass B — fillClonedTask. This is the EXACT pattern that the prior chat
        // claimed was broken with "findById returns null".
        java.util.Map<Long, Long> emptyVarMap = new java.util.HashMap<>();
        for (TaskImpl src : sourceTasks) {
            Long clonedId = taskIdMap.get(src.id);
            cloneTxService.fillClonedTask(src.id, clonedId, targetEcId, emptyVarMap, taskIdMap);
        }

        // assert — every filled row visible
        for (Long clonedId : taskIdMap.values()) {
            TaskImpl t = cloneTxService.debugFindByIdReadOnly(clonedId);
            assertThat(t).as("cloned task " + clonedId + " must be visible after fill").isNotNull();
        }
    }


    @Autowired ai.metaheuristic.ai.dispatcher.repositories.ExecContextGraphRepository ecgRepo;

    /**
     * THIRD-LEVEL REPRO — clones a real produced ExecContext and asserts that
     * for every cloned task, every parentTaskId in its TaskParamsYaml has a
     * matching vertex in the cloned ExecContextGraph. This is the exact
     * invariant TaskVariableInitTxService.getAllParentTaskContextIds checks at
     * runtime (line 184: throws "179.240 vertex wasn\'t found for task #X" if
     * any parent is missing).
     */
    @Test
    void test_clone_everyParentTaskId_hasMatchingVertexInClonedGraph() {
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        step_0_0_produceTasks();
        Long sourceId = getExecContextForTest().id;

        // act
        ExecContextCloneService.CloneResult result = cloneService.cloneExecContext(sourceId);
        Long clonedEcId = result.clonedExecContextId();

        // load cloned graph + cloned tasks
        ExecContextImpl clonedEc = execContextRepository.findByIdNullable(clonedEcId);
        assertThat(clonedEc).isNotNull();
        ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph clonedGraph =
                ecgRepo.findById(clonedEc.execContextGraphId).orElseThrow();
        java.util.List<TaskImpl> clonedTasks =
                taskRepository.findByExecContextIdReadOnly(clonedEcId);

        Set<Long> clonedTaskIds = clonedTasks.stream().map(t -> t.id).collect(Collectors.toSet());

        // find every graph vertex by probing each clonedTaskId AND each potential parentTaskId
        Set<Long> graphVertexTaskIds = new java.util.HashSet<>();
        Set<Long> probeIds = new java.util.HashSet<>(clonedTaskIds);
        for (TaskImpl ct : clonedTasks) {
            ai.metaheuristic.commons.yaml.task.TaskParamsYaml tpy = ct.getTaskParamsYaml();
            if (tpy.task != null && tpy.task.init != null && tpy.task.init.parentTaskIds != null) {
                probeIds.addAll(tpy.task.init.parentTaskIds);
            }
        }
        for (Long pid : probeIds) {
            ai.metaheuristic.ai.dispatcher.data.ExecContextData.TaskVertex v =
                    ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService
                            .findVertexByTaskId(clonedGraph, pid);
            if (v != null) {
                graphVertexTaskIds.add(v.taskId);
            }
        }

        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("DIAG3: clonedTaskIds={} graphVertexTaskIds={}",
                        clonedTaskIds, graphVertexTaskIds);

        // every cloned task row must have a matching graph vertex
        java.util.List<String> missingVertices = new java.util.ArrayList<>();
        for (Long ctId : clonedTaskIds) {
            if (!graphVertexTaskIds.contains(ctId)) {
                missingVertices.add("clonedTaskId=" + ctId + " has no vertex in cloned graph");
            }
        }
        assertThat(missingVertices)
                .as("every cloned task row must have a matching vertex in the cloned graph")
                .isEmpty();

        // every parent referenced by any cloned task\'s params must be a vertex in the cloned graph
        java.util.List<String> missingParents = new java.util.ArrayList<>();
        for (TaskImpl ct : clonedTasks) {
            ai.metaheuristic.commons.yaml.task.TaskParamsYaml tpy = ct.getTaskParamsYaml();
            if (tpy.task == null || tpy.task.init == null || tpy.task.init.parentTaskIds == null) {
                continue;
            }
            for (Long pid : tpy.task.init.parentTaskIds) {
                if (!graphVertexTaskIds.contains(pid)) {
                    missingParents.add("clonedTask=#" + ct.id + " parentTaskId=" + pid
                            + " is NOT a vertex in the cloned graph (task rows: " + clonedTaskIds + ")");
                }
            }
        }
        assertThat(missingParents)
                .as("every parentTaskId on every cloned task must have a matching vertex in the cloned graph "
                    + "(this is the invariant TaskVariableInitTxService line 184 enforces)")
                .isEmpty();
    }


    /**
     * FOURTH-LEVEL DIAGNOSTIC — for the source EC, compare:
     *   - count of TaskImpl rows by execContextId
     *   - count of vertices in the ExecContextGraph
     * If they differ, the graph contains vertices for tasks that don\'t exist
     * as TaskImpl rows (or vice-versa). Then for the cloned EC, the same
     * comparison. The bug surfaces when source-graph has MORE vertices than
     * source tasks → graph rewrite fails to remap them → cloned graph contains
     * source-EC IDs as vertices.
     */
    @Test
    void test_clone_diagnoseSourceGraphVsTasksMismatch() {
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        step_0_0_produceTasks();
        Long sourceId = getExecContextForTest().id;

        // probe source
        ExecContextImpl src = execContextRepository.findByIdNullable(sourceId);
        assertThat(src).isNotNull();
        java.util.List<TaskImpl> srcTasks = taskRepository.findByExecContextIdReadOnly(sourceId);
        Set<Long> srcTaskRowIds = srcTasks.stream().map(t -> t.id).collect(Collectors.toSet());

        ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph srcGraph =
                ecgRepo.findById(src.execContextGraphId).orElseThrow();
        Set<Long> srcGraphVertexIds = collectGraphVertexTaskIds(srcGraph, srcTaskRowIds);

        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("DIAG4 source: ec={} taskRowIds={} graphVertexIds={}",
                        sourceId, srcTaskRowIds, srcGraphVertexIds);

        // act — clone
        ExecContextCloneService.CloneResult result = cloneService.cloneExecContext(sourceId);
        Long clonedId = result.clonedExecContextId();

        // probe clone
        ExecContextImpl clone = execContextRepository.findByIdNullable(clonedId);
        java.util.List<TaskImpl> clonedTasks = taskRepository.findByExecContextIdReadOnly(clonedId);
        Set<Long> clonedTaskRowIds = clonedTasks.stream().map(t -> t.id).collect(Collectors.toSet());
        ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph clonedGraph =
                ecgRepo.findById(clone.execContextGraphId).orElseThrow();
        Set<Long> clonedGraphVertexIds = collectGraphVertexTaskIds(clonedGraph, clonedTaskRowIds);

        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("DIAG4 clone: ec={} taskRowIds={} graphVertexIds={}",
                        clonedId, clonedTaskRowIds, clonedGraphVertexIds);
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("DIAG4 clone graph DOT: {}", clonedGraph.getParams());

        // ASSERTIONS — strict equality
        assertThat(srcGraphVertexIds)
                .as("SOURCE: graph vertex ids must equal task row ids "
                    + "(otherwise graph carries vertices for tasks that don\'t exist)")
                .isEqualTo(srcTaskRowIds);
        assertThat(clonedGraphVertexIds)
                .as("CLONE: graph vertex ids must equal cloned task row ids "
                    + "(otherwise graph rewrite missed some vertices)")
                .isEqualTo(clonedTaskRowIds);
    }

    /**
     * Collect the set of taskIds that appear as vertices in the given graph.
     * We can\'t enumerate them directly (readOnlyGraph is private), so we probe
     * by candidate taskIds. Pass a reasonably large candidate set.
     */
    private static Set<Long> collectGraphVertexTaskIds(
            ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph graph,
            Set<Long> candidates) {
        Set<Long> probeSet = new java.util.HashSet<>(candidates);
        // also probe a wide range to catch source-EC IDs leaking into clone graph
        for (long id = 1; id <= 500; id++) probeSet.add(id);
        Set<Long> found = new java.util.HashSet<>();
        for (Long id : probeSet) {
            ai.metaheuristic.ai.dispatcher.data.ExecContextData.TaskVertex v =
                    ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService
                            .findVertexByTaskId(graph, id);
            if (v != null) {
                found.add(v.taskId);
            }
        }
        return found;
    }


    @Autowired ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphCache ecgCache;

    /**
     * FIFTH-LEVEL DIAGNOSTIC — does ExecContextGraphCache.findById return the
     * REWRITTEN graph (clone-EC IDs) or a stale source-EC version? This is the
     * exact path TaskVariableInitTxService.intiVariables uses at runtime to
     * resolve parent vertices.
     */
    @Test
    void test_clone_ecgCache_returnsRewrittenGraph() {
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        step_0_0_produceTasks();
        Long sourceId = getExecContextForTest().id;

        // act — clone
        ExecContextCloneService.CloneResult result = cloneService.cloneExecContext(sourceId);
        Long clonedId = result.clonedExecContextId();

        ExecContextImpl clonedEc = execContextRepository.findByIdNullable(clonedId);
        assertThat(clonedEc).isNotNull();

        // Read the cloned graph THROUGH THE CACHE (production path)
        ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph viaCache =
                ecgCache.findById(clonedEc.execContextGraphId);
        assertThat(viaCache).isNotNull();

        // Read the cloned graph DIRECTLY from the repository (bypass cache wrapper but L2 still in play)
        ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph viaRepo =
                ecgRepo.findById(clonedEc.execContextGraphId).orElseThrow();

        java.util.List<TaskImpl> clonedTasks = taskRepository.findByExecContextIdReadOnly(clonedId);
        Set<Long> clonedTaskRowIds = clonedTasks.stream().map(t -> t.id).collect(Collectors.toSet());

        Set<Long> verticesViaCache = collectGraphVertexTaskIds(viaCache, clonedTaskRowIds);
        Set<Long> verticesViaRepo  = collectGraphVertexTaskIds(viaRepo, clonedTaskRowIds);

        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("DIAG5: clonedTaskRowIds={} verticesViaCache={} verticesViaRepo={}",
                        clonedTaskRowIds, verticesViaCache, verticesViaRepo);
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("DIAG5 viaCache.params={}", viaCache.getParams());

        assertThat(verticesViaCache)
                .as("ExecContextGraphCache.findById must return the rewritten graph "
                  + "(clone-EC vertex IDs), not the source-EC graph")
                .isEqualTo(clonedTaskRowIds);
    }


    /**
     * SIXTH-LEVEL DIAGNOSTIC — does the cloned EC have rootExecContextId pointing
     * at the SOURCE ec or at itself? If a source EC is self-rooted
     * (rootExecContextId == sourceEcId) and clone copies rootExecContextId
     * verbatim, the clone's rootExecContextId will point at the SOURCE — which
     * may cause downstream code that does "find by rootExecContextId" to pick
     * up source-EC tasks instead of clone-EC tasks.
     */
    @Test
    void test_clone_rootExecContextId_isProperlyPropagated() {
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        step_0_0_produceTasks();
        Long sourceId = getExecContextForTest().id;

        ExecContextImpl src = execContextRepository.findByIdNullable(sourceId);
        assertThat(src).isNotNull();
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("DIAG6 source: id={} rootExecContextId={}",
                        src.id, src.rootExecContextId);

        ExecContextCloneService.CloneResult result = cloneService.cloneExecContext(sourceId);
        ExecContextImpl clone = execContextRepository.findByIdNullable(result.clonedExecContextId());
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("DIAG6 clone: id={} rootExecContextId={}",
                        clone.id, clone.rootExecContextId);

        // If source is self-rooted (rootExecContextId == src.id), then clone is
        // ALSO self-rooted (rootExecContextId == clone.id). Otherwise clone
        // inherits the same root.
        if (java.util.Objects.equals(src.rootExecContextId, src.id)) {
            assertThat(clone.rootExecContextId)
                    .as("source was self-rooted; clone must ALSO be self-rooted "
                      + "(otherwise downstream code finding tasks by rootExecContextId "
                      + "will pick up source-EC tasks, not clone-EC tasks)")
                    .isEqualTo(clone.id);
        } else {
            assertThat(clone.rootExecContextId)
                    .as("source had a separate root; clone should inherit it")
                    .isEqualTo(src.rootExecContextId);
        }
    }


    /**
     * SEVENTH-LEVEL DIAGNOSTIC — for every cloned task, every parentTaskId in
     * its TaskParamsYaml.task.init.parentTaskIds MUST be:
     *   (a) a clone-EC task id (i.e. in the set of clonedTaskRowIds), AND
     *   (b) a vertex in the cloned graph.
     * This is the EXACT runtime invariant TaskVariableInitTxService line 184
     * enforces. If this fails, we have the repro.
     */
    @Test
    void test_clone_parentTaskIds_areAllInCloneGraphAndInCloneTaskRowIds() {
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        step_0_0_produceTasks();
        Long sourceId = getExecContextForTest().id;

        ExecContextCloneService.CloneResult result = cloneService.cloneExecContext(sourceId);
        Long clonedId = result.clonedExecContextId();

        ExecContextImpl clone = execContextRepository.findByIdNullable(clonedId);
        java.util.List<TaskImpl> clonedTasks = taskRepository.findByExecContextIdReadOnly(clonedId);
        Set<Long> clonedTaskRowIds = clonedTasks.stream().map(t -> t.id).collect(Collectors.toSet());
        ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph clonedGraph =
                ecgRepo.findById(clone.execContextGraphId).orElseThrow();

        java.util.List<String> notInRows = new java.util.ArrayList<>();
        java.util.List<String> notInGraph = new java.util.ArrayList<>();

        for (TaskImpl ct : clonedTasks) {
            ai.metaheuristic.commons.yaml.task.TaskParamsYaml tpy = ct.getTaskParamsYaml();
            if (tpy.task == null || tpy.task.init == null || tpy.task.init.parentTaskIds == null) continue;
            for (Long pid : tpy.task.init.parentTaskIds) {
                if (!clonedTaskRowIds.contains(pid)) {
                    notInRows.add("task#" + ct.id + " parentTaskId=" + pid);
                }
                ai.metaheuristic.ai.dispatcher.data.ExecContextData.TaskVertex v =
                        ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService
                                .findVertexByTaskId(clonedGraph, pid);
                if (v == null) {
                    notInGraph.add("task#" + ct.id + " parentTaskId=" + pid);
                }
            }
        }

        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("DIAG7: clonedTasksCount={} notInRows={} notInGraph={}",
                        clonedTasks.size(), notInRows, notInGraph);

        assertThat(notInRows)
                .as("every parentTaskId MUST point at a clone-EC task row")
                .isEmpty();
        assertThat(notInGraph)
                .as("every parentTaskId MUST be a vertex in the cloned graph "
                  + "(this is the runtime invariant TaskVariableInitTxService line 184)")
                .isEmpty();
    }


    /**
     * EIGHTH-LEVEL DIAGNOSTIC — does the clone REWRITE survive when one
     * cloned task has a parentTaskId that belongs to a DIFFERENT source-EC
     * task — i.e. the parent isn\'t in the source-EC task rows? This
     * simulates the "stranger parent" scenario where the source-graph
     * structure is inconsistent with the source TaskParamsYaml.
     *
     * Approach: post-step_0_0_produceTasks, mutate the FIRST cloned
     * task\'s parentTaskIds to include a high (non-existent) ID. Then clone.
     * Expectation: rewriteClonedTaskParentIds keeps the unmapped ID as-is
     * (which is wrong — that\'s the bug). After clone, this stranger ID is
     * NOT in the cloned graph → runtime fails.
     */
    @Test
    void test_clone_strangerParentTaskId_inSourceParams_isPreservedAsIsAfterClone() {
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        step_0_0_produceTasks();
        Long sourceId = getExecContextForTest().id;

        // mutate one source task to have a "stranger" parent ID (9999)
        java.util.List<TaskImpl> srcTasks = taskRepository.findByExecContextIdReadOnly(sourceId);
        TaskImpl victim = srcTasks.get(0);
        ai.metaheuristic.commons.yaml.task.TaskParamsYaml tpy = victim.getTaskParamsYaml();
        if (tpy.task.init == null) {
            tpy.task.init = new ai.metaheuristic.commons.yaml.task.TaskParamsYaml.Init();
        }
        tpy.task.init.parentTaskIds = java.util.List.of(9999L);
        victim.setParams(ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils.UTILS.toString(tpy));
        cloneTxService.debugSaveTask(victim); // helper that does the save in a TX

        // act
        ExecContextCloneService.CloneResult result = cloneService.cloneExecContext(sourceId);

        // probe: cloned task should also have 9999 as parent (NOT in any clone-EC space)
        java.util.List<TaskImpl> clonedTasks = taskRepository
                .findByExecContextIdReadOnly(result.clonedExecContextId());
        java.util.List<Long> strangerParents = new java.util.ArrayList<>();
        for (TaskImpl ct : clonedTasks) {
            ai.metaheuristic.commons.yaml.task.TaskParamsYaml ctpy = ct.getTaskParamsYaml();
            if (ctpy.task != null && ctpy.task.init != null && ctpy.task.init.parentTaskIds != null) {
                for (Long pid : ctpy.task.init.parentTaskIds) {
                    if (pid >= 9000) strangerParents.add(pid);
                }
            }
        }
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("DIAG8: strangerParents={}", strangerParents);

        // The bug is: rewriteClonedTaskParentIds (and rewriteTaskParamsIds) uses
        // taskIdMap.get(pid) and falls back to "pid" if not found. So 9999 stays as 9999.
        // That\'s correct PROVIDED 9999 was a vertex in the source graph too. If not,
        // we have an inconsistency the clone propagates verbatim.
        assertThat(strangerParents).contains(9999L);
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
