# Runbook — full-cycle integration test of git-delivered Functions

Exercises the whole path end to end against a **live Dispatcher and Processor**: author a bundle in a
git repo, import it over MCP, create an ExecContext, and confirm every Task reached `OK`.

Each scenario runs the same four-process chain. An external Function produces a response, the response
is stored through the internal Function `mh.meta-storage`, the record is selected back out as a second
variable, and a second external Function consumes it:

```
external Function #1        ->  response  (+ recKeys)
internal mh.meta-storage    <-  response          action=upsert
internal mh.meta-storage    ->  output2           action=select
external Function #2        <-  output2   ->  verdict
```

❗ The chain is what makes a green run mean something. An external Function that writes a file proves
only that the Processor ran it; the round trip through the meta storage proves the Dispatcher stored
what the Processor produced and handed it back to a later Task in the same ExecContext.

Two scenarios, and they are not variations of one thing — they test different mechanisms:

| | delivery of the bundle | the Function's own payload |
|---|---|---|
| **#1** | git | `sourcing: dispatcher` — packaged into the bundle zip |
| **#2** | git | `sourcing: git` — NOT packaged; fetched from a pinned revision |

❗ Scenario #1 proves git *delivery* changed nothing for an ordinary Function. Scenario #2 proves
git *sourcing* works. Running only #1 tests almost none of the git-Function machinery.

💡 The meta-storage leg (§1) is byte-for-byte identical in both, which is the point of writing it once:
the two runs differ on the git axis and on nothing else, so a difference in outcome has exactly one
possible cause.

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
- The meta-storage MCP tools must be present in the connected client's tool list:
  `mh_list_meta_storage_rec_keys`, `mh_select_meta_storage_record`, `mh_delete_meta_storage_record`.
  They are how §5 verifies the store and how §7 cleans up after it.

⚠️ **After redeploying the Dispatcher, reconnect the MCP client.** An MCP client caches the tool list
and their schemas from when it connected. A tool added by the redeploy is invisible until it
reconnects, and a tool whose arguments changed is still validated against the OLD schema client-side —
so a call can be rejected before it is ever sent, or an argument the server no longer reads can be
demanded. Neither is a Dispatcher problem and no amount of restarting MH fixes it.

⚠️ **The tool list can also vanish MID-RUN.** Observed: `mh_import_bundle_from_git` answered *"not
available in this turn"* between scenario #1 finishing and scenario #2 starting, and every later attempt
to resolve any `mh_*` tool came back empty. Nothing on the Dispatcher had changed, and nothing was left
half-written.

❗ What that costs is bounded, and knowing so is the point: everything the runbook has already done is
persisted OUTSIDE the session. The bundle is pushed, the Functions are registered, a FINISHED
ExecContext stays FINISHED, the meta storage row is written. ✅ Reconnect the client and resume at the
next unrun step. ❌ Do NOT restart from §2 for a scenario that already passed — re-importing forces a
code bump §4.1 says is not owed, and for scenario #2 it destroys the very property §5.3 measures.

❗ **Check the Processor's envs BEFORE authoring anything.** The `env` in `mh-function.yaml` must be a
code the Processor's `env.yaml` defines. `python` and `python-3` are different codes, and getting this
wrong costs a full round trip — see §6.

```
mh_list_processors   ->  envCodes: ["claude-code","java-17","java-25","java-25-128m","python-3"]
```

---

## 1. The meta-storage leg — identical in both scenarios

### 1.1 The two processes

The internal Function is `mh.meta-storage` (`Consts.MH_META_STORAGE_FUNCTION`). It is a Spring bean
collected by `InternalFunctionRegisterService`, so it needs no Function row in the database and nothing
in any bundle — only the `internal` keyword in the `.mhsc`.

```
    store := internal mh.meta-storage {
        name "Store the response of Function #1"
        meta action = "upsert",
             type = "mh-verify.git-cycle",
             content = "response"
        <- response
        timeout 60
    }

    fetch := internal mh.meta-storage {
        name "Select the stored record back as output#2"
        meta action = "select",
             type = "mh-verify.git-cycle",
             keys = "recKeys",
             output = "output2"
        <- recKeys
        -> output2: ext=".json"
        timeout 60
    }
```

The metas, in full:

```
  action      select | upsert                                  (required)
  type        entity kind, a free string                       (required)
  content     input variable holding what to write             (required, action=upsert)
  keys        input variable holding one recKey per line       (optional, action=select)
  output      name of the output variable                      (required, action=select)
```

❗ Every variable a meta names must also be declared on the process. `content` and `keys` are read out
of the task context by name and are only there because of the `<-` line; `output` must match a name on
the `->` line or the task fails with `01.942.100 output variable not found`.

### 1.2 Wire format

Both directions carry a JSON array of `{type, recKey, body}`:

```json
[{"type": "mh-verify.git-cycle", "recKey": "scenario-1-42", "body": "hello, execContextId=42"}]
```

❗ `body` is a STRING throughout and MH never parses it. Whatever is inside a body, and in what
encoding, belongs to the caller. Only the envelope is MH's own contract, and it is the only thing MH
validates — a malformed envelope fails with `01.942.170`, a nonsensical body does not fail at all.

⚠️ The `type` written into a record is **overwritten** by the `type` meta before the row is stored
(`the 'type' meta wins over whatever a record carries` — the `.mhsc` declares the kind). The two can
therefore drift with no consequence, and the `.mhsc` is authoritative. The python below writes the type
anyway, because a reader of the payload should not have to know that rule to understand the file.

### 1.3 What a meta-table is, and the name of this one

❗ **A meta-table is the record or records in `MH_META_STORAGE` carrying one specific value of `TYPE`.
`TYPE` is the name of the meta-table.**

That is the whole definition, and everything awkward about the term follows from it. There is no table
object anywhere: no DDL creates a meta-table, no registry lists one, nothing is dropped when the last
row goes. A meta-table begins to exist the moment a row is written under its name and stops existing
when the last such row is deleted — which is why `mh_list_meta_storage_rec_keys` answers a name nothing
has ever used with an empty list and count 0 rather than an error. Inside one company, `REC_KEY` is the
key of the meta-table and `BODY` is its single, opaque column.

💡 So "create the meta-table" is not an action anyone performs, and "the meta-table is empty" and "the
meta-table does not exist" are the same sentence. The only operations are writing a row under a name and
reading the rows that carry it.

The `TYPE` column is `VARCHAR(50)`, and it is a **column value, never an enum**: a new kind of thing is
a new string, with no DDL, no recompile and no restart. Choosing one is naming, not registration.

This runbook writes under:

```
mh-verify.git-cycle
```

Why this and not something else:

- The `mh-verify.` prefix is already this runbook's namespace — it is the `type` of every Function it
  authors. One grep over the repo and one `mh_list_meta_storage_rec_keys` call now cover everything the
  verification suite owns, and nothing it writes can be mistaken for production data.
- The second segment names the **runbook**, not the scenario. Both scenarios write under the one type
  so that a single `mh_list_meta_storage_rec_keys` shows the whole history of both; the scenario is
  carried in the recKey, where it costs nothing to read.
- 19 characters of the 50 available, lower case, dot-and-hyphen only — the same shape as the Function
  codes beside it, and short enough that a suffix can be added later without a migration.

❗ **Two different things are called "type" here and they are unrelated.** `type: mh-verify.hello` in
`mh-function.yaml` classifies the *Function*; `type = "mh-verify.git-cycle"` in the `.mhsc` names the
*meta storage* column. They share a namespace prefix and nothing else. Do not make them equal — one
being derived from the other is a coincidence waiting to be relied on.

### 1.4 The record key

```
scenario-1-<execContextId>
scenario-2-<execContextId>
```

`(COMPANY_ID, TYPE, REC_KEY)` is the natural key and is what an upsert lands on, which is what makes a
replayed batch idempotent. Keying on the execContextId gives both properties at once: two runs never
collide, and re-running the *same* ExecContext overwrites its own row instead of appending a duplicate.

The execContextId is in the task params (`params['execContextId']`), so Function #1 composes the key
without being told anything.

### 1.5 Caveats worth knowing before the first run

⚠️ **Do not set the `synthetic` meta.** `upsert` honours it and writes to `MH_META_STORAGE_SYNTHETIC`;
`select` does not read it and always reads `MH_META_STORAGE`. A store/select pair with `synthetic` set
therefore returns `[]`, the task finishes `OK`, and the round trip has proved nothing. The runbook uses
the real table and deletes what it wrote (§7).

⚠️ **A blank keys variable selects EVERYTHING.** A missing `keys` meta means "every record of this
type" by design, and an empty key list resolves to the same thing — so a keys file that exists but came
out empty silently widens the select from one record to the whole type. This is why Function #2 asserts
the record *count* and not only the body.

⚠️ **An empty result is a valid output.** `select` with no matches writes `[]`, which is well-formed
JSON, so the task is `OK` and the ExecContext is green. Only Function #2 can tell the difference — see
the exit-code assertions in §2.

---

## 2. Author scenario #1 — git delivery, dispatcher-sourced Functions

Layout, in the staging repo:

```
verify-git-cycle/scenario-1/
    mh-bundle.yaml
    functions/fn-hello-dispatcher/
        mh-function.yaml
        src/mh_verify_hello.py
    functions/fn-check-dispatcher/
        mh-function.yaml
        src/mh_verify_check.py
    source-codes/mh-verify-git-cycle-1-1.2.mhsc
```

`mh-bundle.yaml` — paths are relative to the directory holding this file, and `functions` is scanned
for every descriptor under it, so the second Function needs no extra entry:

```yaml
bundleConfig:
  - path: functions
    type: function
  - path: source-codes
    type: sourceCode
version: 1
```

`functions/fn-hello-dispatcher/mh-function.yaml`:

```yaml
version: 3
function:
  code: mh-verify.hello-dispatcher_1.3
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

`functions/fn-check-dispatcher/mh-function.yaml`:

```yaml
version: 3
function:
  code: mh-verify.check-dispatcher_1.3
  type: mh-verify.check
  env: python-3
  targets:
    mh-default:
      src: src
      file: mh_verify_check.py
  sourcing: dispatcher
  metas:
    - mh.task-params-version: 1
```

`.mhsc` — ❗ the Function code is written here EXACTLY as it appears in `mh-function.yaml`. There is no
translation of any kind between the two.

⚠️ Which constrains how the Function may be named, and the constraint belongs to the DSL rather than to
MH. The mhsc grammar's identifier is `ID : [a-zA-Z_][a-zA-Z0-9_.\-]*`, so a colon cannot be written in
a `.mhsc` at all — a Function intended to be referenced from one must therefore be AUTHORED with a
colon-free code, as these are (`mh-verify.hello-dispatcher_1.3`). `.yaml` SourceCodes have no such
limitation and production uses colons freely: `get-list-of-edition-pairs:2.0.2`,
`aggregate-statistics:2.3` in `jcons` under `releases/edition-maker/em-statistics/`.

💡 `mh.meta-storage` is colon-free for the same reason, and that is not an accident — an internal
Function that could not be named from a `.mhsc` would be unreachable from half the SourceCodes in the
system.

❗ Do not expect `x:1.0` to be reachable as `x_1.0`. `ArtifactCommonUtils.normalizeCode` does map `:` to
`_`, but only for filesystem paths — zip names, the Processor's function directories — never when
resolving a Function reference. Writing `x_1.0` looks up a Function whose code is literally `x_1.0`,
finds nothing, and the SourceCode fails validation.

This SourceCode declares **no source-level input variables**: an ExecContext cannot
produce Tasks while inputs are uninitialized, and `mh_create_exec_context` fails outright if any exist.
The four processes are top-level siblings, which makes them sequential and puts them in one task
context — that is what lets `store` read a variable `hello` produced and `check` read a variable
`fetch` produced.

```
source "mh-verify-git-cycle-1-1.2" {

    hello := mh-verify.hello-dispatcher_1.3 {
        name "External Function #1 — produce the response"
        -> response: ext=".json",
           recKeys: ext=".txt"
        timeout 60
    }

    store := internal mh.meta-storage {
        name "Store the response of Function #1"
        meta action = "upsert",
             type = "mh-verify.git-cycle",
             content = "response"
        <- response
        timeout 60
    }

    fetch := internal mh.meta-storage {
        name "Select the stored record back as output#2"
        meta action = "select",
             type = "mh-verify.git-cycle",
             keys = "recKeys",
             output = "output2"
        <- recKeys
        -> output2: ext=".json"
        timeout 60
    }

    check := mh-verify.check-dispatcher_1.3 {
        name "External Function #2 — consume output#2"
        <- output2
        -> verdict: ext=".txt"
        timeout 60
    }
}
```

Function #1: no inputs, two outputs. The **last** positional argument is always the absolute path to
the params file, and an output goes to `artifacts/<output variable id>` relative to the task dir.

```python
import json, os, sys, yaml
cwd = os.getcwd()
yaml_file = sys.argv[len(sys.argv) - 1]
with open(yaml_file, 'r', encoding='utf-8') as stream:
    params = (yaml.load(stream, Loader=yaml.FullLoader))['task']

def artifact(name):
    var = next(v for v in params['outputs'] if v['name'] == name)
    return os.path.join(cwd, 'artifacts', str(var['id']))

execContextId = str(params['execContextId'])
recKey = 'scenario-1-' + execContextId
body = 'hello, execContextId=' + execContextId

with open(artifact('response'), 'w', encoding='utf-8') as f:
    json.dump([{'type': 'mh-verify.git-cycle', 'recKey': recKey, 'body': body}], f)

with open(artifact('recKeys'), 'w', encoding='utf-8') as f:
    f.write(recKey + '\n')
```

Function #2: one input, one output. ❗ An **input** variable is delivered to `variable/<variable id>`,
not `artifacts/` — the two directories are different and mixing them up produces a file-not-found the
console will name.

❗ The assertions are the reason this Function exists. A non-zero exit code marks the Task `ERROR`
(`FunctionApiData.allFunctionsAreOk`), and that is the only mechanism by which an empty or wrong
round trip can fail the run.

```python
import json, os, sys, yaml
cwd = os.getcwd()
yaml_file = sys.argv[len(sys.argv) - 1]
with open(yaml_file, 'r', encoding='utf-8') as stream:
    params = (yaml.load(stream, Loader=yaml.FullLoader))['task']

inp = next(v for v in params['inputs'] if v['name'] == 'output2')
with open(os.path.join(cwd, 'variable', str(inp['id'])), 'r', encoding='utf-8') as f:
    records = json.load(f)

expected = 'hello, execContextId=' + str(params['execContextId'])

if len(records) != 1:
    print('FAILED: expected exactly 1 record, got ' + str(len(records)))
    sys.exit(1)
if records[0]['body'] != expected:
    print('FAILED: body=[' + records[0]['body'] + '], expected=[' + expected + ']')
    sys.exit(1)

var = next(v for v in params['outputs'] if v['name'] == 'verdict')
with open(os.path.join(cwd, 'artifacts', str(var['id'])), 'w', encoding='utf-8') as f:
    f.write('OK recKey=' + records[0]['recKey'])
print('OK recKey=' + records[0]['recKey'])
```

---

## 3. Author scenario #2 — git-sourced Functions

❗ The payload must live **outside** the directory holding `mh-bundle.yaml`, or the test proves nothing:
the point is that the payload is not packaged and is reached only through the git block.

```
verify-git-cycle/scenario-2/
    mh-bundle.yaml                                  (same as #1)
    functions/fn-hello-git/mh-function.yaml         (no src/ beside it)
    functions/fn-check-git/mh-function.yaml         (no src/ beside it)
    source-codes/mh-verify-git-cycle-2-1.2.mhsc
verify-git-cycle/payload/fn-hello-git/
    src/mh_verify_hello_git.py                      <- outside the bundle
verify-git-cycle/payload/fn-check-git/
    src/mh_verify_check_git.py                      <- outside the bundle
```

```yaml
version: 3
function:
  code: mh-verify.hello-git_1.2
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

```yaml
version: 3
function:
  code: mh-verify.check-git_1.0
  type: mh-verify.check
  env: python-3
  sourcing: git
  git:
    repo: https://github.com/sergmain/metaheuristic-assets.git
    branch: master
    commit: HEAD
    path: verify-git-cycle/payload/fn-check-git
  targets:
    mh-default:
      src: src
      file: mh_verify_check_git.py
  metas:
    - mh.task-params-version: 1
```

The Processor resolves the script as `commits/<sha>/<git.path>/<targets.src>/<targets.file>`, so **both**
the `git` block and `targets` are required. `commit: HEAD` is resolved to a concrete sha by the
Dispatcher when the ExecContext is created, so every Task of that ExecContext runs the same revision.

💡 Both Functions pin `HEAD` of the same repo, so both resolve to the *same* sha in one ExecContext.
That is worth checking in §5: two Tasks materializing the same commit is the property that makes a
multi-Function git-sourced pipeline coherent, and it is not visible from a single-Function run.

The `.mhsc` is scenario #1's with the two external codes swapped and the recKey prefix changed. The
meta-storage leg is unchanged — same function, same metas, same type:

```
source "mh-verify-git-cycle-2-1.2" {

    hello := mh-verify.hello-git_1.2 {
        name "External Function #1 — produce the response"
        -> response: ext=".json",
           recKeys: ext=".txt",
           payloadRev: ext=".txt"
        timeout 60
    }

    store := internal mh.meta-storage {
        name "Store the response of Function #1"
        meta action = "upsert",
             type = "mh-verify.git-cycle",
             content = "response"
        <- response
        timeout 60
    }

    fetch := internal mh.meta-storage {
        name "Select the stored record back as output#2"
        meta action = "select",
             type = "mh-verify.git-cycle",
             keys = "recKeys",
             output = "output2"
        <- recKeys
        -> output2: ext=".json"
        timeout 60
    }

    check := mh-verify.check-git_1.0 {
        name "External Function #2 — consume output#2"
        <- output2
        -> verdict: ext=".txt"
        timeout 60
    }
}
```

The two payload scripts are scenario #1's, with `scenario-1-` replaced by `scenario-2-` in the producer,
plus one addition in the producer that scenario #1 has no use for — a hard-coded revision marker and a
third output variable carrying it:

```python
# ❗ THE REVISION MARKER — bump this literal before every scenario #2 run.
PAYLOAD_MARKER = 'rev-1'

...

with open(artifact('payloadRev'), 'w', encoding='utf-8') as f:
    f.write(PAYLOAD_MARKER)
```

❗ **The script knows NOTHING about its own sourcing, and must not.** It does not read the sha, does not
inspect the path it was materialized into, and cannot tell git delivery from a bundle. A Task on the
Processor is never told how its Function was sourced — that is correct behaviour, not a gap to be
patched. The marker is opaque payload content: a value that changes when the file changes, and nothing
more.

💡 Which is exactly why it proves the thing. An inference the script drew about its own origin would be
the script's claim; a literal it merely copies out is evidence. If a new value reaches the Dispatcher,
the only way it got there is that the new commit ran.

❗ **These two Function codes are registered ONCE and never move again.** That is not a convention of
this runbook — it is the property scenario #2 exists to demonstrate, stated in
`java/legal/docs/descriptions/MH-GIT-DELIVERY-FUNCTION-DESCRIPTION.md` §5: a git-sourced Function keeps
one `functionCode` forever, changes arrive as scripts rather than as `mh-function.yaml`, and **no
version-bump treadmill arises**.

Which is why `mh-verify.hello-git_1.2` keeps the code it already carried while scenario #1 moved to
`_1.3`: its descriptor did not change, so its registration did not either — even though its payload
script was rewritten from top to bottom in the same commit. `mh-verify.check-git_1.0` is a first
registration, and should still read `_1.0` after any number of iterations.

❗ **Bumping these codes between iterations does not merely waste effort — it destroys the test.** A
fresh code each round proves only that a NEW git-sourced Function runs. The claim scenario #2 is making
is stronger and is only observable when the code stays put: that an EXISTING, untouched registration
follows the repo. Change a payload script, push, create a new ExecContext, and the same registered code
must run the new sha. If that step ever needs a re-import, something is wrong with the system, not with
the naming.

---

## 4. Publish

```bash
git add -A && git commit -m "..." && git push
```

❗ **A published Function code is immutable.** Re-importing a bundle whose `mh-function.yaml` changed but
whose `code` did not is a silent no-op — the import answers `ok` with
`295.240 Function ... was already uploaded`, and the OLD definition keeps running. Changing `env`,
`targets`, `sourcing` or the git block therefore means **bumping the code** (`_1.1` -> `_1.2`) and
updating the `.mhsc` that references it. The same applies to a SourceCode uid:
`560.300 the sourceCode with uid ... already exists`.

⚠️ There are now **two** Function codes per scenario and they bump independently. Editing only the
checker still requires the SourceCode uid to move, because the `.mhsc` that names the new checker code
is itself a new document — so in practice a change anywhere costs a uid bump plus the code bump of
whichever Function actually changed.

❗ **Everything above is scenario #1's iteration loop.** Scenario #2 has a different and much shorter
one, because a git-sourced payload is not part of what was published. §4.1 states both.

❗ For a git-sourced Function, editing the payload script alone changes nothing that MH can see: the
descriptor pins `HEAD`, `HEAD` is resolved per ExecContext, and the code did not move. A **new
ExecContext** picks up the new sha; a re-import does not, and a re-run of the old ExecContext does not.

### 4.1 ❗ What actually forces a bump

Two qualifications on the rule above, both easy to over-apply into busywork.

**"Published" means published to THIS Dispatcher, not committed to git.** Immutability is a property of
the row the Dispatcher already holds. A code or a uid that has never been imported here is free however
many times it appears in the repo's history, and re-using it is ordinary iteration on an unpublished
artifact rather than a violation. ❗ Check rather than assume — `mh_list_source_codes` is the whole
answer for a uid, and a `560.300` that cannot fire is not a constraint.

**What forces a CODE bump depends on `sourcing`, because the two sourcings publish different artifacts.**

| | sourcing | what is published | payload-script edit | `mh-function.yaml` edit |
|---|---|---|---|---|
| **scenario #1** | `dispatcher` | descriptor **and** payload — the script travels inside the bundle zip | ❗ forces a bump | forces a bump |
| **scenario #2** | `git` | descriptor **only** — the payload is never packaged | no bump; a NEW ExecContext picks up the new sha | ❗ forces a bump |

The loops that follow from that are different lengths, and this is the practical form of the whole rule:

| to change a Function's script | scenario #1 (`dispatcher`) | scenario #2 (`git`) |
|---|---|---|
| edit the script | ✅ | ✅ |
| bump `code` in `mh-function.yaml` | ✅ required | ❌ **never** |
| update the `.mhsc` to name the new code | ✅ required | ❌ never |
| bump the SourceCode uid | ✅ required | ❌ never |
| `git push` | ✅ | ✅ |
| re-import the bundle | ✅ required | ❌ **never** |
| create a NEW ExecContext | ✅ | ✅ — this is what picks up the new sha |

⚠️ **So the two scenarios are not symmetric, and reading them as symmetric is the mistake to avoid.**
Rewriting the producer script obliges scenario #1 to bump, because for a dispatcher-sourced Function the
script IS part of what was published. It obliges scenario #2 to nothing — its payload was never in a
bundle. Scenario #2 moves only when its own `mh-function.yaml` moves.

The repo's own history holds one bump of each kind, and telling them apart is the whole of this rule:

- `_1.0` -> `_1.1`, commit *"env is python-3"* — an `env: python` -> `python-3` correction, which is an
  `mh-function.yaml` edit. ✅ **Owed by both scenarios.** A descriptor change is the one thing that does
  force a git-sourced Function to move.
- `_1.1` -> `_1.2`, commit *"for a clean re-run"* — nothing changed but the code itself. ❌ **Owed by
  neither**, and by scenario #2 least of all. It is the reflex this section exists to interrupt: a bump
  performed because the previous one was, rather than because something published actually changed.

❗ **Do NOT bump the two scenarios in step, and do not reach for symmetry here.** An earlier revision of
this section called a matching bump harmless — *"a fresh code always imports"* — and that was wrong in
the way that matters most. Registering a fresh code each iteration means scenario #2 only ever
demonstrates that a NEW git-sourced Function runs. It never demonstrates the claim the scenario is FOR:
that an existing, untouched registration follows the repo across a push. That claim is observable only
while the code stays put, so a cosmetic bump silently converts the strongest test in this runbook into
the weakest one — and leaves it green either way, which is why nothing catches it.

💡 The two scenarios therefore drift apart over iterations, by design. Scenario #1's codes climb every
time its script changes; scenario #2's stay where they were first registered. ❗ Codes that no longer
match across the scenarios is the CORRECT steady state, not drift to be tidied up.

---

## 5. Import, run, verify

```
mh_import_bundle_from_git(repo="https://github.com/sergmain/metaheuristic-assets.git",
                          path="verify-git-cycle/scenario-1")
   -> ok: true, infoMessages: ["Validation result: OK"]
```

The company is taken from the authenticated principal — there is no `companyId` argument, and a bundle
cannot be imported into the management company (#1), where SourceCodes are common to all companies and
appear on no ordinary company's source-codes page.

```
mh_list_source_codes    -> note the id AND the companyId of the row carrying the new uid

mh_create_exec_context(companyId=<companyId>, sourceCodeId=<id>)
                        -> returns execContextId, stateName STARTED
```

❗ `companyId` is REQUIRED on `mh_create_exec_context` and has no default. Take it from
`mh_list_source_codes`, which carries it on every row; `mh_import_bundle_from_git` also echoes it back in
its own answer. ⚠️ It cannot come from `mh_get_exec_context_info` — that call needs an execContextId,
which does not exist until this one has already succeeded.

`createExecContextAndStart` already leaves it STARTED;
`mh_exec_context_target_state(execContextId=<id>, state="STARTED")` is only for one that isn't. Then poll:

```
mh_get_exec_context_info(execContextId=<id>)          -> stateName, completedOn, companyId
mh_get_exec_context_task_state(execContextTaskStateId=<id from above>)
```

✅ **Pass:** `stateName: FINISHED` and every entry in `states:` is `OK`. Nothing in `ERROR`. **Five**
entries, not one — the four processes of the chain plus one more. A run that finishes with fewer has
skipped part of the chain.

💡 The fifth entry is not declared anywhere in the `.mhsc`: MH appends its own finishing Task, and
`mh_get_task_info` on it reports `functionCode: mh.finish` with `coreId: null`. Like the two
meta-storage Tasks it runs inside the Dispatcher, and it is what carries the ExecContext to `FINISHED`.
Counting the processes in the `.mhsc` and expecting that many Tasks is therefore off by one, every time.

⚠️ **Poll more than once before reading anything into a partial result.** A measured scenario #1 run
reached `FINISHED` about 38 seconds after creation; at the 20-second mark it read
`1: OK, 2: OK, 3: OK, 4: NONE, 5: PRE_INIT`, because the Processor was still fetching the checker
Function. ❗ `NONE` on a Task whose Function has just been published is the ordinary shape of a run in
progress, not a gate problem — §6 is for a `NONE` that PERSISTS across several polls.

⚠️ `mh_get_exec_context_task_state` takes the **execContextTaskStateId** from the previous call, not the
execContextId. They happen to be equal on a clean database, which hides a wrong argument until they
diverge.

💡 Keep the `companyId`. Every meta-storage tool takes it, and it is the
first segment of the natural key — reading the wrong company's store returns an empty list rather than
an error.

### 5.1 Confirm the store actually holds the record

The ExecContext being green already implies it: Function #2 exits non-zero unless exactly one record
came back with the expected body. This step confirms the row **outside** the pipeline, which is a
different claim — that the write survived the transaction and is addressable by its natural key.

```
mh_list_meta_storage_rec_keys(companyId=<id>, type="mh-verify.git-cycle", synthetic=false)
   -> recKeys: ["scenario-1-<execContextId>"]

mh_select_meta_storage_record(companyId=<id>, type="mh-verify.git-cycle",
                              recKey="scenario-1-<execContextId>", synthetic=false)
   -> body: "hello, execContextId=<execContextId>", gen, version, updatedAt
```

✅ **Pass:** the recKey list contains this run's key, and the body matches what Function #1 wrote.

❗ `synthetic=false` is required and has no default. The two tables carry identical columns and allocate
ids independently, so a wrong flag returns a different row or an empty list — never an error. §1.5 is
why this runbook writes to the non-synthetic table in the first place.

💡 Re-running the SAME ExecContext is the idempotency check: the recKey list must not grow, and
`version`/`gen` must move. A second key means the upsert appended instead of landing on the natural key.

### 5.2 Confirm WHERE the Functions ran

❗ **A green ExecContext is not yet proof for scenario #2.** Both scenarios finish identically, so
confirm WHERE the Function ran: `functionExecResultsExcerpt` carries its console, including the
absolute path of the script.

```
mh_get_task_info(taskId=<id>)
```

For scenario #1 that path is under the unpacked bundle:

```
processor\resources\<dispatcher>\function\mh-verify.hello-dispatcher__1.3\src\mh_verify_hello.py
```

❗ **Note the DOUBLED underscore, and do not correct it.** The directory is not the Function code — it is
`ArtifactCommonUtils.normalizeCode` of the code. That mapping doubles an underscore and turns a colon
into a single one (`a_b` -> `a__b`, `a:b` -> `a_b`), so the two can never collide on disk; it is pinned by
`ArtifactCommonUtilsTest.test_normalizeCodeDoublesAnUnderscore`. Reading a path back, halve the
underscores: `mh-verify.hello-dispatcher__1.3` is the Function `mh-verify.hello-dispatcher_1.3`.
⚠️ Grepping the console for the code as written in `mh-function.yaml` therefore misses the path.

For scenario #2 it is under the materialized commit, and the sha in it must be the revision pushed in
§4 — that is the whole claim of git sourcing, that a payload never packaged into any bundle ran from a
pinned revision:

```
processor\resources\git\<repo-code>\commits\<sha>\verify-git-cycle\payload\fn-hello-git\src\mh_verify_hello_git.py
```

✅ Check the checker too: `mh-verify.check-git_1.0` must report the **same** `<sha>` as
`mh-verify.hello-git_1.2`. Two shas in one ExecContext would mean `HEAD` was resolved per Task rather
than per ExecContext.

⚠️ The two internal Tasks have no console of this kind — an internal Function runs inside the
Dispatcher, so its evidence is the Dispatcher log (`01.942.120 select ...`, `01.942.220 upsert ...`) and
the row itself, not a script path.

### 5.3 ❗ Scenario #2 only: prove a NEW commit was used, with NOTHING re-registered

§5.2 shows a sha in a path. This step shows that the sha **moved on its own** — which is the claim
scenario #2 exists for and the one thing §5 and §5.2 cannot demonstrate from a single run. It needs two
ExecContexts and one push between them.

| | do | expect |
|---|---|---|
| 1 | run scenario #2 as per §5 | `payloadRev` = `rev-1` |
| 2 | edit ONLY `PAYLOAD_MARKER` in `payload/fn-hello-git/src/mh_verify_hello_git.py` to `rev-2`; `git push` | — |
| 3 | ❗ import NOTHING. Touch no `mh-function.yaml`, no `.mhsc`, no uid. | — |
| 4 | `mh_create_exec_context` on the SAME sourceCodeId as step 1 | `payloadRev` = `rev-2` |

✅ **Pass:** step 4 emits `rev-2` while `mh-verify.hello-git_1.2` is the same registered Function code
throughout, never re-imported. `mh_get_task_info` on the two `hello` Tasks shows two different shas, and
the second is what `git rev-parse HEAD` returns after step 2.

❌ **Fail:** step 4 emits `rev-1`. The ExecContext resolved a stale revision — `HEAD` was pinned once and
cached rather than resolved per ExecContext, and every later run of this SourceCode is running dead code.
⚠️ Note this failure is otherwise INVISIBLE: the ExecContext is green, all five Tasks are `OK`, the
meta-storage round trip passes, and §5.2 still shows a plausible sha. Only the marker separates the two
outcomes.

❗ **Read the stored VARIABLE, not the console.** Both carry the value, and they are different claims:
the console is what the Function SAID about itself, the variable is what the Dispatcher RECEIVED and
STORED. Only the second closes the loop, and it is the one to assert on.

```
mh_get_exec_context_info(execContextId=<id>)          -> execContextVariableStateId
mh_get_exec_context_variable_state(execContextVariableStateId=<id>)
        -> find the `hello` task, then the entry in `outs` with `nm: payloadRev` -> its `id`
mh_get_variable_content(variableId=<id>)              -> content
```

A confirmed run of the second ExecContext:

```
outs: [{"id":10,"nm":"response",...},{"id":11,"nm":"recKeys",...},{"id":12,"nm":"payloadRev","i":true,"n":false}]
mh_get_variable_content(variableId=12)  ->  content: "rev-2", returnedBytes: 5, truncated: false
```

💡 `i` (inited) and `n` (nullified) are worth reading on the way past: a variable that exists but was
never written shows `i: false`, which is a different failure from one holding a stale value.

⚠️ The console remains useful as corroboration — the Function prints `payloadRev=<value>` and the
`functionExecResultsExcerpt` of the §5.2 `mh_get_task_info` call carries it with no extra lookup. Use it
to cross-check, not as the assertion.

💡 Step 3 is the whole point and is worth stating as a prohibition rather than an omission. Every
instinct from scenario #1 says to bump and re-import; doing so here would make the run pass for the
wrong reason — a freshly registered Function obviously runs its own code, which proves nothing about
whether an untouched one follows the repo. §4.1 is the rule this step enforces.

Repeat §5 for `verify-git-cycle/scenario-2`.

⚠️ Scenario #2 is slower to start: the Processor must fetch and materialize the pinned commit before the
Task can be assigned. Allow a poll interval or two before treating a delay as a failure.

---

## 6. ❗ When a Task sits in NONE and is never assigned

A Task that is never assigned reports nothing on the Task itself — `execState` stays `NONE` and
`coreId` stays null indefinitely. **The Execution Gate is where the reason lives.** Check it before
reading any log:

```
mh_execution_gate_status
```

or the UI at `/#/dispatcher/execution-gate`. ✅ On a healthy run both halves come back empty
(`records: []`, `rejections: []`) — nothing is being withheld.

⚠️ Read an empty result against the ExecContexts that exist. Empty is only good news if the Tasks you
expected to run have actually finished; an ExecContext that was never created is also silent here. It returns active blocks and the recent rejection reasons
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
correcting `env` in `mh-function.yaml` — and per §4, **bump the Function code**, because editing the
descriptor alone will not take effect. Note the old ExecContext keeps retrying its unassignable Task and
keeps filling the gate; stop it with `mh_exec_context_target_state(execContextId=<id>, state="STOPPED")`
so the view reflects the new run.

**`functions_not_ready` — transient.** No Processor has reported holding that Function at the revision
the Task is pinned to. Normal for the first minute of a git-sourced run while the commit is fetched. If
it persists, check that the Processor has `git` on `PATH` and that the repo is trusted (§0).

**Other checks, in order of cost:**

- `mh_list_processors` — is a Processor connected at all? Is `lastSeen` current? Is it `blacklisted`,
  and what is `blacklistReason`? Are all its cores `busy`? For `not_enough_quotas`, the same tool
  reports `quotas` (limit, defaultValue, disabled, per-tag amounts). This tool is the analogue of the
  `/#/dispatcher/processors` page and carries every column it shows, with the raw status yaml replaced
  by the parsed fields worth acting on.
- `mh_get_task_info` — `functionExecResultsExcerpt` holds the Function's own console for a Task that
  did run and failed.
- Processor log — error codes `01.817.*` cover git fetch and materialization failures.
  ⚠️ Those failures are logged and retried, never marked terminal, so a Task blocked on them stays in
  `NONE` and the Processor log is the only place the cause appears.

### 6.1 When the meta-storage Task is the one that failed

An internal Function never reaches a Processor, so none of the above applies to it. Its error codes are
`01.942.*` and they name the cause directly:

- `01.942.020` / `01.942.040` — the `action` or `type` meta is missing or blank on the process.
- `01.942.060` — `action` is neither `select` nor `upsert`. A typo, and it fails at run time rather
  than at validation.
- `01.942.080` — `action=select` with no `output` meta.
- `01.942.100` — the `output` meta names a variable the process does not declare on its `->` line.
- `01.942.140` / `01.942.160` — `action=upsert` with no `content` meta, or the named variable is empty.
- `01.942.170` — the content variable is not well-formed JSON. It names the variable and its length,
  which is usually enough to see that Function #1 wrote plain text where an envelope was expected.
- `01.942.180` / `01.942.200` — a record in the envelope has a blank `recKey` or a null `body`.

⚠️ A `select` that matches nothing is NOT in this list. It writes `[]` and succeeds — §1.5.

---

## 7. Cleaning up between runs

Bumping codes each iteration leaves stale SourceCodes and ExecContexts behind. They are harmless except
that an unassignable Task from an earlier run keeps firing gate rejections and obscures the current one.
Stop those ExecContexts — `mh_exec_context_target_state(execContextId=<id>, state="STOPPED")` — rather
than reading around them. ⚠️ The stop cascades to every ExecContext sharing the same root, so stopping a
parent stops the children it spawned through `mh.exec-source-code`. Stop the root, not each child.

Each run also leaves one meta storage row per scenario, and unlike a stale ExecContext these accumulate
under a single type. Delete them by natural key:

```
mh_delete_meta_storage_record(companyId=<id>, type="mh-verify.git-cycle",
                              recKey="scenario-1-<execContextId>", synthetic=false)
```

⚠️ Leaving them is not harmful — the recKey carries the execContextId, so nothing collides — but a
select that ever runs without `keys` returns the whole type, and a type full of dead runs makes that
output useless. `mh_list_meta_storage_rec_keys` is how to see what has accumulated; bodies are not read,
so it stays cheap however long the list has grown.
