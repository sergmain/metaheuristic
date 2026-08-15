/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.functions;

import ai.metaheuristic.ai.dispatcher.event.events.NewWebsocketEvent;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueEvent;
import ai.metaheuristic.ai.dispatcher.execution_gate.ExecutionGateService;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParamsUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterizes the bug where a processor registering a new functionCode via
 * processRequest did NOT publish any event, leaving tasks waiting on that
 * function stuck in TaskQueue under WS profile.
 */
public class FunctionRepositoryNewlyReadyEventTest {

    @Test
    public void test_processRequest_publishes_events_when_new_function_becomes_ready() throws Exception {
        List<Object> captured = new ArrayList<>();
        ApplicationEventPublisher publisher = captured::add;

        // A fresh gate per test, so readiness starts empty without reaching into a static field.
        // The readiness registry is backed by no table, which is why none of the collaborators below
        // are touched on this path.
        ExecutionGateService executionGateService = new ExecutionGateService(null, null, null, null, null);

        FunctionRepositoryDispatcherService svc =
            new FunctionRepositoryDispatcherService(null, null, null, publisher, executionGateService);

        // Mark a function as active so registerReadyFunctionCodesOnProcessor accepts it
        java.lang.reflect.Field af = FunctionRepositoryDispatcherService.class.getDeclaredField("activeFunctions");
        af.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> activeFunctions = (java.util.Set<String>) af.get(null);
        activeFunctions.add("fn-x");
        // no readiness to clear: this gate has never seen fn-x, so processor 42L is genuinely new
        FunctionRepositoryRequestParams p = new FunctionRepositoryRequestParams();
        p.processorId = 42L;
        p.functionCodes = List.of("fn-x");
        String data = FunctionRepositoryRequestParamsUtils.UTILS.toString(p);

        svc.processRequest(data, "127.0.0.1");

        boolean hasWs = captured.stream().anyMatch(e -> e instanceof NewWebsocketEvent);
        boolean hasFind = captured.stream().anyMatch(e -> e instanceof FindUnassignedTasksAndRegisterInQueueEvent);
        assertTrue(hasWs, "NewWebsocketEvent published on first-time function readiness");
        assertTrue(hasFind, "FindUnassignedTasksAndRegisterInQueueEvent published");

        assertTrue(executionGateService.isProcessorReady("fn-x", 42L),
                "readiness must be recorded in the gate, which is where it now lives");

        // Second call with same (fn, processor) — already ready — must NOT re-publish
        captured.clear();
        svc.processRequest(data, "127.0.0.1");
        assertTrue(captured.isEmpty(), "no events on repeat call with already-ready function/processor");
    }

    @Test
    public void test_readinessIsPerProcessor() {
        // a second Processor reporting the same function is new again, and must re-notify
        List<Object> captured = new ArrayList<>();
        ApplicationEventPublisher publisher = captured::add;

        ExecutionGateService executionGateService = new ExecutionGateService(null, null, null, null, null);
        FunctionRepositoryDispatcherService svc =
            new FunctionRepositoryDispatcherService(null, null, null, publisher, executionGateService);

        assertTrue(executionGateService.recordFunctionReadiness("fn-y", 1L));
        assertFalse(executionGateService.recordFunctionReadiness("fn-y", 1L),
                "the same pair a second time is not new");
        assertTrue(executionGateService.recordFunctionReadiness("fn-y", 2L),
                "a different Processor for the same function IS new");

        assertTrue(executionGateService.isProcessorReady("fn-y", 1L));
        assertTrue(executionGateService.isProcessorReady("fn-y", 2L));
        assertFalse(executionGateService.isProcessorReady("fn-y", 3L));
        assertFalse(executionGateService.isProcessorReady("fn-unknown", 1L));
    }

    @Test
    public void test_forgetFunctionReadinessDropsEverythingAboutAFunction() {
        ExecutionGateService executionGateService = new ExecutionGateService(null, null, null, null, null);

        executionGateService.recordFunctionReadiness("fn-z", 1L);
        executionGateService.recordFunctionReadiness("fn-z", 2L);
        assertTrue(executionGateService.isProcessorReady("fn-z", 1L));

        executionGateService.forgetFunctionReadiness("fn-z");

        assertFalse(executionGateService.isProcessorReady("fn-z", 1L));
        assertFalse(executionGateService.isProcessorReady("fn-z", 2L));
    }

    @Test
    public void test_seedFunctionReadinessMarksNoProcessorReady() {
        ExecutionGateService executionGateService = new ExecutionGateService(null, null, null, null, null);

        executionGateService.seedFunctionReadiness("fn-seeded");

        assertEquals(1, executionGateService.functionReadinessCount());
        assertFalse(executionGateService.isProcessorReady("fn-seeded", 1L),
                "seeding starts a lifetime, it does not claim any Processor is ready");
    }
}
