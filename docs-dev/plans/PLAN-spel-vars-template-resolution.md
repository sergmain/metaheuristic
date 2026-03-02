# Plan: SpEL `{{expr}}` Template Resolution in SourceCode Process Metas

## Problem

The flat SourceCode YAML (`mhdg-rg-flat-short-1.0.0.yaml`) has massive duplication of process definitions in case of using subProcesses extensively. Each level (0-5) of subProcess repeats identical process structures with only variable name suffixes changing (`reqJson0` → `reqJson1`, `requirementId0` → `requirementId1`).

YAML anchors (`&anchor`/`*alias`) can't solve this because variable names embedded in
meta values differ per level. With SpEL resolution against MH variables,
the same anchored meta text like `variable-for-parentId: parentId{{level}}` resolves to
`parentId1` at level 1 and `parentId2` at level 2.

## Scope

Only metas ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml.Process#metas, that end up in
`TaskParamsYaml.task.metas` and then `TaskFileParamsYaml.task.metas`, need `{{expr}}`
resolution. Other metas in SourceCode (e.g. ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml.SourceCode#metas) are NOT evaluated — they stay as-is for now.

## Solution Overview

Placeholders like `{{level}}` inside meta values reference MH variables. Resolution uses
the existing `EvaluateExpressionLanguage.evaluate()` infrastructure which requires Spring context
(internalFunctionVariableService, globalVariableService, variableTxService, variableRepository)
to look up variable values in MH.

Stub for evaluation:
```java
    // in EvaluateExpressionLanguage.evaluate() we need only to use variableService.setVariableAsNull(v.id)
    // because mh.evaluate doesn't have any output variables
    Object obj = EvaluateExpressionLanguage.evaluate(
        simpleExecContext.execContextId, taskId, taskParamsYaml.task.taskContextId, p.condition,
        internalFunctionVariableService, globalVariableService, variableTxService, variableRepository,
            (v) -> VariableSyncService.getWithSyncVoidForCreation(v.id,
                    ()-> variableTxService.setVariableAsNull(taskId, v.id)));
```

No new SourceCodeParamYaml version needed. No changes to MetaUtils or commons classes.

### Step 1: Create ElEvaluator

Create `ai.metaheuristic.ai.dispatcher.el.ElEvaluator` utility class:
- `resolve(String template, ...)` — finds `{{expr}}` patterns, evaluates each via
  `EvaluateExpressionLanguage.evaluate()` against MH variable context, replaces with result.
  If no placeholders found, returns original string unchanged.
- `resolveMetas(List<Map<String,String>> metas, ...)` — iterates meta entries, calls `resolve()`
  on each value. Returns original list unchanged if no resolution needed.
- All variable lookups go through the MH Spring-managed services (no standalone `vars` map).

### Step 2: Integrate in TaskProducingService

Usage of code for evaluation must be put in method
`ai.metaheuristic.ai.dispatcher.task.TaskProducingService#createTaskHelper`
after `TODO p0 2026-03-01 add SpEL evaluation of Metas here`.

The required Spring beans (internalFunctionVariableService, globalVariableService,
variableTxService, variableRepository) must be available in TaskProducingService or passed
to ElEvaluator methods.

### Step 3: Unit Tests

Tests require Spring Boot context (reference: `SouthbridgeControllerTest` for context setup pattern).
File: `apps/metaheuristic/src/test/java/ai/metaheuristic/ai/dispatcher/el/ElEvaluatorTest.java`

Tests must create real MH variables in the test context so that SpEL resolution through
`EvaluateExpressionLanguage` can look them up.

Test cases:

1. `resolve("parentId{{level}}", ...)` where variable `level`=1 → `"parentId1"`
2. `resolve("parentId{{level}}", ...)` where variable `level`=0 → `"parentId0"`
3. `resolve("check-objectives-{{level + 1}}", ...)` where variable `level`=0 → `"check-objectives-1"`
4. `resolve("no-placeholders", ...)` → `"no-placeholders"` (unchanged, no placeholders)
5. `resolve("parentId{{level}}", ...)` with no variables defined → `"parentId{{level}}"` (unchanged)
6. `resolve("{{a}}{{b}}", ...)` where `a`="hello", `b`="World" → `"helloWorld"` (multiple)
7. `resolveMetas([{variable-for-parentId: parentId{{level}}}], ...)` where `level`=2
   → `[{variable-for-parentId: parentId2}]`
8. `resolveMetas` with no variables returns original list unchanged
9. `resolve("topLevelReqs{{level}}", ...)` where variable `level`=3 → `"topLevelReqs3"`

