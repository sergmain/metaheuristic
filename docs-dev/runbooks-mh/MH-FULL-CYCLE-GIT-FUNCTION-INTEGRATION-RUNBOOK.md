# Runbook — full-cycle integration test of git-delivered Functions

Exercises the whole path end to end against a **live Dispatcher and Processor**: author a bundle in a
git repo, import it over MCP, create an ExecContext, and confirm every Task reached `OK`.

Two scenarios, and they are not variations of one thing — they test different mechanisms:

| | delivery of the bundle | the Function's own payload |
|---|---|---|
| **#1** | git | `sourcing: dispatcher` — packaged into the bundle zip |
| **#2** | git | `sourcing: git` — NOT packaged; fetched from a pinned revision |

❗ Scenario #1 proves git *delivery* changed nothing for an ordinary Function. Scenario #2 proves
git *sourcing* works. Running only #1 tests almost none of the git-Function machinery.

Everything here is synthetic. ❗ No call-cc, no Claude Code, no proprietary Function — the cycle must be
verifiable without them, so the verification Functions are plain python that write one output variable.

---

## 0. Prerequisites

- A Dispatcher with the `mcp` profile active, and its MCP endpoint reachable (`/rest/v1/mcp`).
- A Processor connected to it, with `git` on `PATH` and a python interpreter.
- A staging git repo the Dispatcher can clone. `metaheuristic-assets` is the one in use.
- ❗ For scenario #2, the payload repo must be trusted: `mh.function.trusted.git-repo` must contain a
  prefix of the repo url in the Function's `git.repo`. The default list already contains
  `https://github.com/sergmain/metaheuristic-assets`.

❗ **Check the Processor's envs BEFORE authoring anything.** The `env` in `mh-function.yaml` must be a
code the Processor's `env.yaml` defines. `python` and `python-3` are different codes, and getting this
wrong costs a full round trip — see §5.

```
mh_list_processors   ->  envCodes: ["claude-code","java-17","java-25","java-25-128m","python-3"]
```

---

## 1. Author scenario #1 — git delivery, dispatcher-sourced Function

Layout, in the staging repo:

```
verify-git-cycle/scenario-1/
    mh-bundle.yaml
    functions/fn-hello-dispatcher/
        mh-function.yaml
        src/mh_verify_hello.py
    source-codes/mh-verify-git-cycle-1-1.1.mhsc
```

`mh-bundle.yaml` — paths are relative to the directory holding this file:

```yaml
bundleConfig:
  - path: functions
    type: function
  - path: source-codes
    type: sourceCode
version: 1
```

`mh-function.yaml`:

```yaml
version: 3
function:
  code: mh-verify.hello-dispatcher_1.1
  type: mh-verify.hello
  env: python-3
  targets:
    mh-default:
      src: src
      file: mh_verify_hello.py
  sourcing: dispatcher
  metas:
    - mh.task-params-version: 1
```

`.mhsc` — ❗ a colon is not legal in an identifier, so a Function code written `x:1.0` is referenced as
`x_1.0` here. This SourceCode declares **no source-level input variables**: an ExecContext cannot
produce Tasks while inputs are uninitialized, and `mh_create_exec_context` fails outright if any exist.

```
source "mh-verify-git-cycle-1-1.1" {
    hello := mh-verify.hello-dispatcher_1.1 {
        -> greeting: ext=".txt"
        timeout 60
    }
}
```

The Function itself: no inputs, one output. The **last** positional argument is always the absolute path
to the params file, and the output goes to `artifacts/<output variable id>` relative to the task dir.

```python
import os, sys, yaml
cwd = os.getcwd()
yaml_file = sys.argv[len(sys.argv) - 1]
with open(yaml_file, 'r', encoding='utf-8') as stream:
    params = (yaml.load(stream, Loader=yaml.FullLoader))['task']
var = next(v for v in params['outputs'] if v['name'] == 'greeting')
with open(os.path.join(cwd, 'artifacts', str(var['id'])), 'w', encoding='utf-8') as f:
    f.write('hello, execContextId=' + str(params['execContextId']))
```

---

## 2. Author scenario #2 — git-sourced Function

❗ The payload must live **outside** the directory holding `mh-bundle.yaml`, or the test proves nothing:
the point is that the payload is not packaged and is reached only through the git block.

```
verify-git-cycle/scenario-2/
    mh-bundle.yaml                                  (same as #1)
    functions/fn-hello-git/mh-function.yaml         (no src/ beside it)
    source-codes/mh-verify-git-cycle-2-1.1.mhsc
verify-git-cycle/payload/fn-hello-git/
    src/mh_verify_hello_git.py                      <- outside the bundle
```

```yaml
version: 3
function:
  code: mh-verify.hello-git_1.1
  type: mh-verify.hello
  env: python-3
  sourcing: git
  git:
    repo: https://github.com/sergmain/metaheuristic-assets.git
    branch: master
    commit: HEAD
    path: verify-git-cycle/payload/fn-hello-git
  targets:
    mh-default:
      src: src
      file: mh_verify_hello_git.py
  metas:
    - mh.task-params-version: 1
```

The Processor resolves the script as `commits/<sha>/<git.path>/<targets.src>/<targets.file>`, so **both**
the `git` block and `targets` are required. `commit: HEAD` is resolved to a concrete sha by the
Dispatcher when the ExecContext is created, so every Task of that ExecContext runs the same revision.

---

## 3. Publish

```bash
git add -A && git commit -m "..." && git push
```

❗ **A published Function code is immutable.** Re-importing a bundle whose `mh-function.yaml` changed but
whose `code` did not is a silent no-op — the import answers `ok` with
`295.240 Function ... was already uploaded`, and the OLD definition keeps running. Changing `env`,
`targets`, `sourcing` or the git block therefore means **bumping the code** (`_1.1` -> `_1.2`) and
updating the `.mhsc` that references it. The same applies to a SourceCode uid:
`560.300 the sourceCode with uid ... already exists`.

---

## 4. Import, run, verify

```
mh_import_bundle_from_git(repo="https://github.com/sergmain/metaheuristic-assets.git",
                          path="verify-git-cycle/scenario-1")
   -> ok: true, infoMessages: ["Validation result: OK"]
```

The company is taken from the authenticated principal — there is no `companyId` argument, and a bundle
cannot be imported into the management company (#1), where SourceCodes are common to all companies and
appear on no ordinary company's source-codes page.

```
mh_list_source_codes                            -> note the id of the new uid
mh_create_exec_context(sourceCodeId=<id>)       -> returns execContextId, stateName STARTED
```

`createExecContextAndStart` already leaves it STARTED; `mh_start_exec_context` is only for one that
isn't. Then poll:

```
mh_get_exec_context_info(execContextId=<id>)          -> stateName, completedOn
mh_get_exec_context_task_state(execContextTaskStateId=<id from above>)
```

✅ **Pass:** `stateName: FINISHED` and every entry in `states:` is `OK`. Nothing in `ERROR`.

Confirm the Function actually ran rather than being skipped — `functionExecResultsExcerpt` carries its
console, including the path it was executed from, which is how scenario #2 is distinguished from #1:

```
mh_get_task_info(taskId=<id>)
```

Repeat §4 for `verify-git-cycle/scenario-2`.

⚠️ Scenario #2 is slower to start: the Processor must fetch and materialize the pinned commit before the
Task can be assigned. Allow a poll interval or two before treating a delay as a failure.

---

## 5. ❗ When a Task sits in NONE and is never assigned

A Task that is never assigned reports nothing on the Task itself — `execState` stays `NONE` and
`coreId` stays null indefinitely. **The Execution Gate is where the reason lives.** Check it before
reading any log:

```
mh_execution_gate_status
```

or the UI at `/#/dispatcher/execution-gate`. It returns active blocks and the recent rejection reasons
with exemplar Tasks. ⚠️ `bucketsPresent` matters more than `count`: a reason present across the whole
window has stopped being transient however small its volume.

**`interpreter_is_undefined` — actionable.** The Function's `env` names an interpreter the Processor
does not define. ❗ Request the Processor info and compare its declared environment against the `env` in
`mh-function.yaml`:

```
mh_list_processors    -> envCodes for each Processor
```

The exemplar names the Task, the Function code and the offending env, e.g.
`task #1 — mh-verify.hello-dispatcher_1.0 — python` against `envCodes: [..., "python-3"]`. Fix by
correcting `env` in `mh-function.yaml` — and per §3, **bump the Function code**, because editing the
descriptor alone will not take effect. Note the old ExecContext keeps retrying its unassignable Task and
keeps filling the gate; stop it with `mh_stop_exec_context` so the view reflects the new run.

**`functions_not_ready` — transient.** No Processor has reported holding that Function at the revision
the Task is pinned to. Normal for the first minute of a git-sourced run while the commit is fetched. If
it persists, check that the Processor has `git` on `PATH` and that the repo is trusted (§0).

**Other checks, in order of cost:**

- `mh_list_processors` — is a Processor connected at all? Is `lastSeen` current? Is it `blacklisted`,
  and what is `blacklistReason`? Are all its cores `busy`?
- `mh_get_task_info` — `functionExecResultsExcerpt` holds the Function's own console for a Task that
  did run and failed.
- Processor log — error codes `01.817.*` cover git fetch and materialization failures.
  ⚠️ Those failures are logged and retried, never marked terminal, so a Task blocked on them stays in
  `NONE` and the Processor log is the only place the cause appears.

---

## 6. Cleaning up between runs

Bumping codes each iteration leaves stale SourceCodes and ExecContexts behind. They are harmless except
that an unassignable Task from an earlier run keeps firing gate rejections and obscures the current one.
Stop those ExecContexts (`mh_stop_exec_context`) rather than reading around them.
