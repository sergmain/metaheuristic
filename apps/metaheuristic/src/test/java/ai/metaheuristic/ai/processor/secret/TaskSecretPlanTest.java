/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package ai.metaheuristic.ai.processor.secret;

import ai.metaheuristic.commons.security.SealedSecret;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Pure-unit tests for {@link TaskSecretPlan#plan}. Real values, real
 * {@link TaskParamsYaml.TaskYaml} objects, lambda for cache lookup.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class TaskSecretPlanTest {

    private static SealedSecret dummySealed() {
        return new SealedSecret(SealedSecret.VERSION_1, new byte[]{1}, new byte[]{2}, new byte[]{3});
    }

    private static TaskParamsYaml.FunctionConfig fnWithApi(String code, String keyCode) {
        TaskParamsYaml.FunctionConfig fc = new TaskParamsYaml.FunctionConfig();
        fc.code = code;
        if (keyCode != null) {
            fc.api = new TaskParamsYaml.Api(keyCode);
        }
        return fc;
    }

    private static TaskParamsYaml.TaskYaml taskWithMain(TaskParamsYaml.FunctionConfig main) {
        TaskParamsYaml.TaskYaml t = new TaskParamsYaml.TaskYaml();
        t.function = main;
        return t;
    }

    private static Function<String, SealedSecret> empty() {
        return k -> null;
    }

    private static Function<String, SealedSecret> oneEntry(String key, SealedSecret value) {
        Map<String, SealedSecret> m = new HashMap<>();
        m.put(key, value);
        return m::get;
    }

    @Test
    public void test_plan_returnsNoSecretNeeded_whenCompanyIdIsZero() {
        TaskParamsYaml.TaskYaml task = taskWithMain(fnWithApi("main-fn", "openai_api_key"));
        TaskSecretPlan.Plan p = TaskSecretPlan.plan(task, 0L, empty());
        assertEquals(TaskSecretPlan.Kind.NO_SECRET_NEEDED, p.kind());
    }

    @Test
    public void test_plan_returnsNoSecretNeeded_whenNoFunctionHasApi() {
        TaskParamsYaml.TaskYaml task = taskWithMain(fnWithApi("main-fn", null));
        TaskSecretPlan.Plan p = TaskSecretPlan.plan(task, 7L, empty());
        assertEquals(TaskSecretPlan.Kind.NO_SECRET_NEEDED, p.kind());
    }

    @Test
    public void test_plan_returnsAwaiting_whenCacheMisses() {
        TaskParamsYaml.TaskYaml task = taskWithMain(fnWithApi("main-fn", "openai_api_key"));
        TaskSecretPlan.Plan p = TaskSecretPlan.plan(task, 7L, empty());
        assertEquals(TaskSecretPlan.Kind.AWAITING, p.kind());
        assertEquals("openai_api_key", p.keyCode());
    }

    @Test
    public void test_plan_returnsReady_forMainPhase_whenCacheHits() {
        SealedSecret sealed = dummySealed();
        TaskParamsYaml.TaskYaml task = taskWithMain(fnWithApi("main-fn", "openai_api_key"));
        TaskSecretPlan.Plan p = TaskSecretPlan.plan(task, 7L, oneEntry("openai_api_key", sealed));
        assertEquals(TaskSecretPlan.Kind.READY, p.kind());
        assertEquals("main", p.phase());
        assertEquals("openai_api_key", p.keyCode());
        assertSame(sealed, p.sealed());
    }

    // Deleted with the removal of pre/post Functions: the pre-phase, post-phase and
    // MULTI_SECRET_VIOLATION tests all needed a Task carrying more than one Function, which
    // TaskParamsYaml can no longer express. A Task now has exactly one Function, so at most one
    // api key can be in play and the multi-secret branch is unreachable by construction.

    @Test
    public void test_plan_blankKeyCodeIsTreatedAsNoApi() {
        TaskParamsYaml.TaskYaml task = taskWithMain(fnWithApi("main-fn", "   "));
        TaskSecretPlan.Plan p = TaskSecretPlan.plan(task, 7L, empty());
        assertEquals(TaskSecretPlan.Kind.NO_SECRET_NEEDED, p.kind());
    }

    @Test
    public void test_plan_nullApiIsTreatedAsNoApi() {
        TaskParamsYaml.FunctionConfig fc = new TaskParamsYaml.FunctionConfig();
        fc.code = "main-fn";
        fc.api = null;
        TaskParamsYaml.TaskYaml task = taskWithMain(fc);
        TaskSecretPlan.Plan p = TaskSecretPlan.plan(task, 7L, empty());
        assertEquals(TaskSecretPlan.Kind.NO_SECRET_NEEDED, p.kind());
    }
}
