# Savepoint: Fix SKIPPED Propagation

## Status Overview

### Committed (17cdf51):
- [x] ExecContextGraphService - SKIPPED cascade to all children
- [x] TaskWithInternalContextService - skipTask() save + publish event
- [x] TestGraphSkipPropagation - linear DAG test (passes)

### Uncommitted (in working tree):
- [x] ExecContextGraphService - comment improvement (diff present)
- [x] TaskStateService - remove SKIPPED from anyParentError (diff present)

### Tests Added (all pass):
- [x] Test 1: testSkippedPropagationToSubProcessTasks - linear DAG, SKIPPED cascades to all non-leaf descendants
- [x] Test 2: testSkippedPropagationWithSubProcessContext - RG architecture with wrapper mh.nop, SKIPPED at deeper ctx only affects sub-process

### SourceCode YAML fix:
- [x] mhdg-rg-flat-short-1.0.0.yaml - wrapped conditional mh.nop-objectives inside plain mh.nop-objectives-wrapper (option 1)

### Regression check:
- [x] TestGraphSkipOnErrorWithMhFinish#testLinearDagWithErrorSkipsNonLeafButNotMhFinish - still passes

### Key design decisions documented:
- Leaf filter excludes only mh.finish (the only leaf in any DAG — always added implicitly)
- SKIPPED at ctx="1" cascades to ALL descendants (same + deeper context) except mh.finish
- To isolate conditional SKIP from main flow, wrap conditional mh.nop inside plain mh.nop (option 1)
- This pushes the SKIP to a deeper context level, so main-flow siblings are unaffected

### Remaining:
- [ ] Commit uncommitted changes (ExecContextGraphService comment, TaskStateService fix, tests, YAML)
- [ ] Option 2 (meta 'status propagation: children_only') deferred — option 1 sufficient for now
