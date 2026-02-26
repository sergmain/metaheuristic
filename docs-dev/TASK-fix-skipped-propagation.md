# Task: Fix SKIPPED status propagation in sub-Process tasks

## Problem Summary

When `mh.nop` with a condition evaluates to false, the task is correctly marked SKIPPED.
However, the SKIPPED state was NOT propagating to all descendant tasks in the sub-process.
Only the first sub-process task was being marked, leaving subsequent tasks stuck in NONE/PRE_INIT.
Additionally, `taskContextId` was not being properly incremented for sub-Process tasks.

This caused the ExecContext to get stuck — downstream tasks (like `mh.finish`) could never
fire because they had un-finished parents in NONE state.

## Context: RG Objective Processing Block

The scenario is from the Requirement Governance pipeline. SourceCode YAML defines:

```
mhdg-rg.check-objectives  ->  mh.nop-objectives (condition: hasObjectives)
                                   subProcesses:
                                     mhdg-rg.evaluate-objective (call-cc)
                                     mhdg-rg.store-objective-result
                               ->  mhdg-rg.read-req-1  ->  ...  ->  mh.finish
```

When `hasObjectives=false`, `mh.nop-objectives` is SKIPPED. All sub-process tasks
(`evaluate-objective`, `store-objective-result`) must also be SKIPPED so that
`mh.finish` can fire.

## Root Cause Analysis (Two Problems)

### Problem 1: SKIPPED was not cascading to descendants in the graph

**File:** `ExecContextGraphService.java` (~line 286)

Before the fix, when a task state was set to SKIPPED in `updateTaskExecState()`, there
was NO code to cascade SKIPPED to children. The code had:

```java
else if (execState == EnumsApi.TaskExecState.SKIPPED) {
    log.info("915.015 TaskExecState for task #{} is SKIPPED", tv.taskId);
    // nothing else happened
}
```

For ERROR, there was already `setStateForAllChildrenTasksInternal(...)` being called.
SKIPPED needed the same treatment.

**Fix applied (commit 17cdf51):** Added SKIPPED cascade identical to ERROR:
```java
else if (execState == EnumsApi.TaskExecState.SKIPPED) {
    // propagate SKIPPED to all children tasks in the same context branch.
    setStateForAllChildrenTasksInternal(graph, stateParamsYaml, taskId, status,
        EnumsApi.TaskExecState.SKIPPED, taskWithState.taskContextId);
}
```

### Problem 2: SKIPPED parent was blocking sibling tasks from advancing to INIT

**File:** `TaskStateService.java` (~line 116)

`changeTaskStateToInitForChildrenTasksTxEventInternal()` decides whether a child task
can advance to INIT by checking all parent states. The original code treated SKIPPED
the same as ERROR:

```java
if (state == EnumsApi.TaskExecState.ERROR || state == EnumsApi.TaskExecState.SKIPPED) {
    anyParentError = true;
}
```

This is WRONG because SKIPPED is normal flow control (condition evaluated to false),
not an error. A SKIPPED parent (like `mh.nop-objectives`) should NOT block its
siblings (like `mhdg-rg.read-req-1`) from advancing.

**Fix applied (uncommitted, in working tree):** Removed SKIPPED from the error check:
```java
if (state == EnumsApi.TaskExecState.ERROR) {
    anyParentError = true;
}
```

## How the Flow Works After Fix

1. `mhdg-rg.check-objectives` runs, writes `hasObjectives=false` → completes OK
2. `mh.nop-objectives` is picked up, condition `hasObjectives` evaluates to false
3. `TaskWithInternalContextEventService.processInternalFunction()` calls `skipTask(taskId)`
4. `skipTask()` sets state to SKIPPED, publishes `UpdateTaskExecStatesInExecContextTxEvent`
5. Graph update in `ExecContextGraphService.updateTaskExecState()` cascades SKIPPED to
   all non-leaf descendants of the skipped task (via `setStateForAllChildrenTasksInternal`)
6. `TaskExecStateService.updateTasksStateInDb()` persists SKIPPED states for those descendants
7. `ChangeTaskStateToInitForChildrenTasksTxEvent` fires for the SKIPPED task
8. `TaskStateService.changeTaskStateToInitForChildrenTasksTxEventInternal()` checks siblings
   — SKIPPED parent does NOT block, so sibling tasks (like `read-req-1`) advance to INIT
9. `mh.finish` eventually becomes assignable when all parents are in finished states

## Files Modified

### Committed (17cdf51):

1. **`apps/metaheuristic/src/main/java/ai/metaheuristic/ai/dispatcher/exec_context_graph/ExecContextGraphService.java`**
   - Line ~286: Added SKIPPED → cascade to all children (same as ERROR path)
   - Method: `updateTaskExecState()` inner lambda

2. **`apps/metaheuristic/src/main/java/ai/metaheuristic/ai/dispatcher/internal_functions/TaskWithInternalContextService.java`**
   - Method `skipTask()`: Added `task.setCompleted(1)`, `task.setCompletedOn(...)`,
     `task.setResultReceived(1)`, `taskTxService.save(task)`,
     and `eventPublisherService.publishUpdateTaskExecStatesInGraphTxEvent()`
   - Before fix, `skipTask()` set state but did NOT save or publish graph event

3. **`apps/metaheuristic/src/test/java/ai/metaheuristic/ai/graph/TestGraphSkipPropagation.java`** (NEW)
   - Integration test verifying SKIPPED cascade in a linear DAG
   - Tests: task1(OK) → task2(SKIPPED) → task3(SKIPPED) → task4(SKIPPED) → task5(NONE/assignable)

### Uncommitted (in working tree):

4. **`apps/metaheuristic/src/main/java/ai/metaheuristic/ai/dispatcher/exec_context_graph/ExecContextGraphService.java`**
   - Comment improvement only (no logic change from committed version)

5. **`apps/metaheuristic/src/main/java/ai/metaheuristic/ai/dispatcher/task/TaskStateService.java`**
   - Line ~116: Removed SKIPPED from `anyParentError` check
   - SKIPPED parents no longer block siblings from advancing to INIT
   - Updated comments to explain: "SKIPPED is normal flow control, not an error"

## Files to READ for Full Understanding

### Core flow (task state transitions):

1. **`TaskWithInternalContextEventService.java`** — Entry point for internal function execution.
   `processInternalFunction()` evaluates the condition, calls `skipTask()` if false.
   - Key: lines ~185-215 (condition evaluation block)
   - Key: `extractBooleanFromVariableHolder()` for condition variable parsing

2. **`TaskWithInternalContextService.java`** — `skipTask()` method sets SKIPPED state.
   `preProcessing()` sets task to IN_PROGRESS and inits output variables.
   `storeResult()` called on successful execution.

3. **`TaskExecStateService.java`** — `changeTaskState()` method publishes events:
   - `ChangeTaskStateToInitForChildrenTasksTxEvent` when any finished state is reached
   - `SetTaskExecStateInQueueTxEvent` for task queue updates
   - `FindUnassignedTasksAndRegisterInQueueTxEvent` when state becomes NONE

4. **`TaskStateService.java`** — Handles `ChangeTaskStateToInitForChildrenTasksEvent`.
   `changeTaskStateToInitForChildrenTasksTxEventInternal()` — the method that decides
   whether child tasks can advance to INIT by checking ALL parent states.
   THIS IS WHERE SKIPPED-vs-ERROR distinction matters.

5. **`ExecContextGraphService.java`** — Graph operations.
   - `updateTaskExecState()` (line ~270) — sets state in graph, cascades to children
   - `setStateForAllChildrenTasksInternal()` (line ~654) — finds all descendants,
     filters by taskContextId prefix, excludes leaf nodes, marks as target state
   - `findAllForAssigning()` — finds tasks where all parents are finished

### Sub-process creation:

6. **`SubProcessesTxService.java`** — `processSubProcesses()` — called by NopFunction.
   Creates tasks for sub-processes defined in YAML template.

7. **`NopFunction.java`** — Simple internal function that calls `subProcessesTxService.processSubProcesses()`.
   When condition is false, NopFunction.process() is NEVER called — `skipTask()` is called instead.

### Graph structure:

8. **`ExecContextData.java`** — `TaskVertex` record with `taskId` and `taskContextId`.
   `ExecContextDAC` wraps the JGraphT DAG.

9. **`ContextUtils.java`** — `getLevel()` extracts context level from taskContextId.
   Used in `setStateForAllChildrenTasksInternal` to filter descendants by context branch.

### Event flow:

10. **`UpdateTaskExecStatesInExecContextTxEvent.java`** — Published after task state change.
    Handled by `ExecContextTopLevelService.updateTaskExecStatesInExecContext()` which calls
    `execContextGraphService.updateTaskExecState()` and then `taskExecStateService.updateTasksStateInDb()`.

11. **`ChangeTaskStateToInitForChildrenTasksTxEvent.java`** — Published when task reaches
    finished state. Handled by `TaskStateService.changeTaskStateToInitForChildrenTasksTxEvent()`.

## Key Design Insight: `setStateForAllChildrenTasksInternal` Filters

The method `setStateForAllChildrenTasksInternal()` at line ~654 has two important filters:

```java
Set<ExecContextData.TaskVertex> setFiltered = set.stream()
    .filter(tv -> !execContextDAC.graph().outgoingEdgesOf(tv).isEmpty()   // (A) exclude leaf nodes
        && (context==null ? true                                           // (B) if no context, include all
            : ContextUtils.getLevel(tv.taskContextId).startsWith(context))) // (C) filter by context branch
    .collect(Collectors.toSet());
```

- **(A) Leaf exclusion:** `mh.finish` is always a leaf (no outgoing edges) and is never
  marked SKIPPED. This is correct — `mh.finish` must fire after all non-skipped work completes.

- **(B-C) Context filtering:** When `taskContextId` is provided (from `taskWithState.taskContextId`),
  only descendants in the same context branch are affected. This ensures that SKIPPED
  propagation from `mh.nop-objectives` in context `"1,2"` only affects tasks under `"1,2"`
  and not tasks in sibling context `"1,3"`.

## Remaining Concerns / What to Verify

### 1. taskContextId increment for sub-processes
The original report mentioned "taskContextId wasn't increased for sub-Process". This needs
verification — when `mh.nop` calls `processSubProcesses()`, the sub-tasks get incremented
`taskContextId` (e.g., `"1,2"` → `"1,2,1"`, `"1,2,2"`). But when the NOP is SKIPPED,
`processSubProcesses()` is NEVER called (skipTask() is called instead), so sub-process tasks
may not have been created at all, meaning there's nothing to cascade SKIPPED to.

**This is the critical question:** Are sub-process tasks pre-created at ExecContext init time
(by `TaskProducingService.produceAndStartAllTasks()`) or dynamically when NopFunction runs?

If pre-created: they exist with taskContextId already set, SKIPPED propagation via graph is correct.
If dynamic: they were never created, so there are no tasks to mark SKIPPED, and the problem is
that `mh.finish` has a parent edge to the NOP task which IS now SKIPPED, so `mh.finish` should
fire if SKIPPED is treated as a finished state.

**To investigate:** Look at `TaskProducingService.createTasksForSubProcesses()` and trace
whether sub-process tasks exist BEFORE `NopFunction.process()` is called.

### 2. findAllForAssigning must treat SKIPPED as "finished"
Verify that `findAllForAssigning()` considers a SKIPPED parent as "finished" so that
downstream tasks can be assigned. Check `EnumsApi.TaskExecState.isFinishedState()`:

```java
public static boolean isFinishedState(EnumsApi.TaskExecState state) {
    return state == OK || state == ERROR || state == ERROR_WITH_RECOVERY || state == SKIPPED;
}
```

SKIPPED is already in `isFinishedState()` — this is correct.

### 3. Test coverage gaps
The existing `TestGraphSkipPropagation` tests a FLAT linear DAG (no actual sub-processes).
A more realistic test should simulate the actual RG scenario:
- Batch-line-splitter creating per-item sub-tasks
- mh.nop with condition=false inside a sub-process
- Verify that the mh.nop's sub-sub-process tasks get SKIPPED
- Verify that sibling processes AFTER the mh.nop continue normally

### 4. The uncommitted changes in TaskStateService.java
The working tree has changes that remove SKIPPED from the `anyParentError` check.
These changes need to be committed and tested. Specifically, verify that when a
SKIPPED mh.nop and a successful check-objectives are both parents of `read-req-1`,
the `read-req-1` task advances to INIT (not blocked).

## Test Plan

### Existing test (committed):
- `TestGraphSkipPropagation.testSkippedPropagationToSubProcessTasks()` — linear DAG
- POM dir: `java/metaheuristic/apps/metaheuristic`
- Run: `clear && cd java/metaheuristic/apps/metaheuristic && mvn -q test -Dtest="ai.metaheuristic.ai.graph.TestGraphSkipPropagation#testSkippedPropagationToSubProcessTasks"`

### Tests to add:

1. **Test SKIPPED parent does NOT block siblings:**
   DAG: task1(OK) and task2(SKIPPED) both are parents of task3.
   Verify task3 advances to INIT (not blocked by SKIPPED parent).
   This tests the `TaskStateService` fix.

2. **Test with actual sub-process context:**
   Create tasks with nested taskContextId (e.g., `"1"`, `"1,2"`, `"1,2,1"`, `"1,2,2"`).
   SKIP task at `"1,2"`. Verify all `"1,2,*"` descendants are SKIPPED.
   Verify tasks in `"1,3"` are NOT affected.

3. **Integration test with real SourceCode YAML:**
   Use `test-condition-related-1.0.yaml` or create a new YAML with mh.nop + condition.
   Full ExecContext lifecycle test verifying that when condition=false, the sub-process
   is properly skipped and mh.finish fires.

## How to Run Tests

POM directory: `java/metaheuristic/apps/metaheuristic`

```bash
# Existing graph test
clear && cd java/metaheuristic/apps/metaheuristic && mvn -q test -Dtest="ai.metaheuristic.ai.graph.TestGraphSkipPropagation#testSkippedPropagationToSubProcessTasks"

# All graph tests
clear && cd java/metaheuristic/apps/metaheuristic && mvn -q test -Dtest="ai.metaheuristic.ai.graph.TestGraphSkipPropagation"

# Related condition test
clear && cd java/metaheuristic/apps/metaheuristic && mvn -q test -Dtest="ai.metaheuristic.ai.source_code.TestSourceCodeService"
```

## How to Verify in Production

1. Create an RG project, run full decomposition
2. After ExecContext reaches FINISHED, submit an objective on a requirement
3. ExecContext restarts, `check-objectives` runs
4. For requirements WITHOUT objectives: `hasObjectives=false` → `mh.nop` SKIPPED
5. Verify in ExecContext state UI: all tasks in the skipped objective block show SKIPPED
6. Verify: `read-req`, `decompose`, `store-reqs` continue normally (not blocked)
7. Verify: `mh.finish` fires and ExecContext reaches FINISHED

## Related Tasks

- **TASK-fix-condition-evaluation.md** — Fixes SpEL condition evaluation so that
  `condition: hasObjectives` works (without ternary workaround). Without this fix,
  the condition only works with `condition: 'hasObjectives ? true : false'`.
  Both tasks are needed for the objective processing pipeline to work correctly.
