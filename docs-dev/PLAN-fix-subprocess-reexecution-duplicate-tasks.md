# PLAN: Fix SubProcess Re-execution Creating Duplicate Tasks

## Problem Summary

When a wrapper task (e.g. `mh.nop-objectives-wrapper-2`) with subProcesses is reset and
re-executed, the current fix in `SubProcessesTxService.processSubProcesses` correctly removes
old subProcess **grandchildren** from the graph. However, the old subProcess task itself
(e.g. Task#289 `mh.nop-objectives-2`) is NOT removed, AND a new duplicate (Task#290) is created.

## Observed Behavior (from screenshot)

```
1,2,5,6|1|1#0      Task#289 (mh.nop-objectives-2) — NONE — old, NOT deleted
1,2,5,6,7|1|1|0#0  Task#291 — OK, Task#292 — ERROR — work tasks, under #289
1,2,5,6|1|1#0 [#1] Task#290 — OK — duplicate wrapper, shouldn't exist
```

## Root Cause

The nesting is two levels deep:
```
mh.nop-objectives-wrapper-2 (parent, ctx="1,2,5|1#1")
  → mh.nop-objectives-2 (ctx="1,2,5,6|1|1#0")    ← has its own subProcesses
    → mhdg-rg.evaluate-objective (ctx="1,2,5,6,7|1|1|0#0")
    → mhdg-rg.store-objective-result (ctx="1,2,5,6,7|1|1|0#0")
```

When `mh.nop-objectives-wrapper-2` re-executes `processSubProcesses`:
1. `findDirectDescendants(wrapper)` returns [Task#289 (old nop-objectives-2), ...]
2. Current fix removes #289's children (evaluate-obj, store-obj-result) from graph
3. Current fix filters #289 from `createEdges` targets
4. BUT #289 itself is NOT removed from the graph — persists with NONE state
5. `createTasksForSubProcesses` creates Task#290 (new nop-objectives-2) — DUPLICATE
6. Task#290 runs and creates #291/#292 but they may attach to wrong parent

## Approach: Option A — Detect and Reuse Existing SubProcess Tasks (Recommended)

In `processSubProcesses`, before `createTasksForSubProcesses`:
1. Detect old subProcess tasks among direct descendants (by `subProcessCtxPrefix` match)
2. Instead of creating new tasks, reset existing ones to NONE
3. Their old children were already removed by `removeOldSubProcessChildren`
4. When the reused task re-executes, it creates fresh children itself
5. Skip `createTasksForSubProcesses` entirely when old tasks are found and reused

This avoids both duplicates and orphans.

## Alternative: Option B — Full Delete and Recreate

Remove old subProcess task (#289) entirely from graph AND DB, then
`createTasksForSubProcesses` creates everything fresh. More destructive but simpler —
no "reuse" detection needed. Requires DB task deletion + variable cleanup.

## Key Files

- `SubProcessesTxService.processSubProcesses` — main fix location
- `InternalFunctionService.getSubProcesses` → `findDirectDescendants`
- `TaskProducingService.createTasksForSubProcesses` — task creation
- `ExecContextGraphService.removeOldSubProcessChildren` — current removal logic
- `ExecContextTaskResettingService.resetTask` — for Option A reset

## Context from Previous Chats

### Chat: "Task state transition bug in DAG processing" (UUID: 7f3a8b2e)
- Root cause: `processSubProcesses` creating duplicate topology on wrapper re-execution
- `findDirectDescendants(wrapper)` returns old children + real downstream tasks
- `createEdges` connected new children to old children → backward edges (155→154)
- Fix 1: filter old children by `subProcessCtxPrefix` before `createEdges`
- Fix 2: remove old children from graph before creating new ones
- Fix 3 (this plan): handle the wrapper-level task itself, not just grandchildren

### Chat: "Propagating error state to child tasks" (478b01ae)
- ERROR_WITH_RECOVERY → ERROR transition path
- `resetTasksWithErrorForRecovery` — `triesAfterError` controls retry count
- Graph state vs DB state desynchronization — reconciliation resets "hanging" tasks
- SKIPPED propagation to unreachable tasks

### Key MH Patterns
- `processSubProcesses` called by `TaskWithInternalContextEventService`
- `findDirectDescendants` = one hop in graph, not transitive
- `createEdges(lastIds, descendants)` connects last new task to all descendants
- Internal functions run on Dispatcher side with direct DB access
- `ExecContextCache.save()` required after state changes (L2 cache coherence)

### Diagnostic Logging (999.xxx, 995.xxx) — TO REMOVE AFTER FIX
- 999.010 — resetTasksWithErrorForRecovery decision
- 999.020/021 — resetTask with stack trace
- 999.030 — finishTaskAsError
- 999.040/050 — changeTaskStateToInitForChildren
- 999.060/070 — changeTaskState / downgrade prevention
- 999.080 — graph state updates
- 999.090 — reconciliation resets
- 995.100/110/120 — subProcess child removal logging

## Next Steps

1. Verify in graph dump whether #291/#292 are children of #289 or #290
2. Decide: Option A (reuse) vs Option B (delete+recreate)
3. Write unit test for two-level nesting re-execution scenario
4. Implement fix
5. Remove all diagnostic logging (999.xxx, 995.xxx)
