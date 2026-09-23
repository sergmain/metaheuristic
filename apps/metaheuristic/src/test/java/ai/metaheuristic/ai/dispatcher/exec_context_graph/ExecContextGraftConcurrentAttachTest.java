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

package ai.metaheuristic.ai.dispatcher.exec_context_graph;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Two {@link ExecContextGraftService#attachGroup} calls grafting under the SAME target of the SAME ExecContext at
 * the same time - what parallel manual requirement inserts into one RG STAGE do to that STAGE's clone.
 *
 * <p>{@code graftSetup} computes the fresh line ctx - the next sibling ctx not yet taken by any task of the EC -
 * before {@code attachGroup} takes any lock; the locks are taken only for Stage 1, which creates the line's tasks.
 * Two callers that both compute the ctx before either creates its tasks compute the same one.
 *
 * <p>The interleaving is produced with the real lock, not with timing: the test holds the EC's
 * {@link ExecContextSyncService} write lock, lets both callers run {@code graftSetup} and queue on that lock -
 * observed as the two threads parked in {@code WriteLock.lock} called from
 * {@code ExecContextSyncService.getWithSyncVoid} - and only then releases it.
 *
 * <p>V3 harness: extends PreparingSourceCode -> PreparingCore -> MhSharedItTest (shared Spring context + H2 DB;
 * no @DirtiesContext; cleanup inherited). Every assertion reads real state: what each call returned or threw, and
 * the tasks the EC holds at each line ctx.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@AutoConfigureCache
public class ExecContextGraftConcurrentAttachTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private InternalFunctionService internalFunctionService;
    @Autowired private ExecContextGraftService execContextGraftService;
    @Autowired private TaskRepository taskRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang(
                "/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    /** True once {@code t} is parked acquiring an EC's write lock inside {@code ExecContextSyncService.getWithSyncVoid}. */
    private static boolean parkedOnExecContextLock(Thread t) {
        if (t.getState() != Thread.State.WAITING) {
            return false;
        }
        final StackTraceElement[] frames = t.getStackTrace();
        for (int i = 0; i + 1 < frames.length; i++) {
            if (ReentrantReadWriteLock.WriteLock.class.getName().equals(frames[i].getClassName())
                    && "lock".equals(frames[i].getMethodName())
                    && ExecContextSyncService.class.getName().equals(frames[i + 1].getClassName())
                    && "getWithSyncVoid".equals(frames[i + 1].getMethodName())) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void test_twoConcurrentAttachGroupsUnderOneTarget() throws Exception {
        // 1. create EC + produce real tasks WITHOUT starting (STOPPED - the graft target state).
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result =
                txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        final Long ecId = getExecContextForTest().id;

        ExecContextSyncService.getWithSyncVoid(ecId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId, () ->
                                txSupportForTestingService.produceTasksWithoutStarting(getSourceCode(), ecId))));
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));

        // 2. find a target task whose getSubProcesses resolves a non-empty SEQUENTIAL body.
        final ExecContextImpl ec = getExecContextForTest();
        final ExecContextApiData.SimpleExecContext sec = ec.asSimple();
        Long targetTaskId = null;
        InternalFunctionData.ExecutionContextData ecdTarget = null;
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(ecId)) {
            InternalFunctionData.ExecutionContextData ecd =
                    internalFunctionService.getSubProcesses(sec, t.getTaskParamsYaml(), t.id);
            if (ecd.internalFunctionProcessingResult.processing == Enums.InternalFunctionProcessing.ok
                    && !ecd.subProcesses.isEmpty()
                    && ecd.process.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                targetTaskId = t.id;
                ecdTarget = ecd;
                break;
            }
        }
        assertNotNull(targetTaskId, "FIXTURE: no task with a graftable sub-process body found in the produced EC");
        final List<String> bodyCodesSorted = ecdTarget.subProcesses.stream()
                .map(v -> v.process).sorted().toList();

        // 3. two grafts under the same target, both queued on the EC lock after their graftSetup.
        final Long target = targetTaskId;
        final List<ExecContextGraftService.GraftResult> results = new CopyOnWriteArrayList<>();
        final List<Throwable> failures = new CopyOnWriteArrayList<>();
        final Runnable graft = () -> {
            try {
                results.add(execContextGraftService.attachGroup(
                        ecId, target,
                        ExecContextGraftService.GroupRef.fromTargetSubProcesses(),
                        List.of(), List.of(),
                        ExecContextGraftService.Driver.PLACE_NOW));
            }
            catch (Throwable th) {
                failures.add(th);
            }
        };

        final ReentrantReadWriteLock.WriteLock lock = ExecContextSyncService.getWriteLock(ecId);
        final Thread a;
        final Thread b;
        lock.lock();
        try {
            a = Thread.ofPlatform().daemon().name("graft-a").start(graft);
            b = Thread.ofPlatform().daemon().name("graft-b").start(graft);
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(50))
                    .until(() -> parkedOnExecContextLock(a) && parkedOnExecContextLock(b));
        }
        finally {
            lock.unlock();
        }
        a.join(Duration.ofSeconds(60));
        b.join(Duration.ofSeconds(60));
        assertFalse(a.isAlive(), "PHASE #1: graft A must have finished");
        assertFalse(b.isAlive(), "PHASE #1: graft B must have finished");

        assertEquals(List.of(), failures.stream().map(Throwable::getMessage).toList(),
                "PHASE #1: neither graft may fail");
        assertEquals(2, results.size(), "PHASE #1: both grafts must return a result");
        final String ctxA = results.get(0).lineCtxId();
        final String ctxB = results.get(1).lineCtxId();

        // characterization: both grafts computed the same line ctx, so it carries the body twice
        // desired: each graft gets its own fresh sibling line ctx, carrying the body exactly once
        assertNotEquals(ctxA, ctxB,
                "PHASE #2: the two grafts must occupy distinct line ctxs, observed " + ctxA + " / " + ctxB);
        assertEquals(bodyCodesSorted, lineProcessCodesSorted(ecId, ctxA),
                "PHASE #2: line " + ctxA + " must carry the body exactly once");
        assertEquals(bodyCodesSorted, lineProcessCodesSorted(ecId, ctxB),
                "PHASE #2: line " + ctxB + " must carry the body exactly once");
    }

    private List<String> lineProcessCodesSorted(Long ecId, String lineCtxId) {
        List<String> codes = new ArrayList<>();
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(ecId)) {
            if (lineCtxId.equals(t.getTaskParamsYaml().task.taskContextId)) {
                codes.add(t.getTaskParamsYaml().task.processCode);
            }
        }
        codes.sort(String::compareTo);
        return codes;
    }
}
