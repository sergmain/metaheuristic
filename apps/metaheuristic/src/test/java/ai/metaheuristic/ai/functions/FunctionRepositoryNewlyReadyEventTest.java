package ai.metaheuristic.ai.functions;

import ai.metaheuristic.ai.dispatcher.event.events.NewWebsocketEvent;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueEvent;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParamsUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

        FunctionRepositoryDispatcherService svc =
            new FunctionRepositoryDispatcherService(null, null, null, publisher);

        // Mark a function as active so registerReadyFunctionCodesOnProcessor accepts it
        java.lang.reflect.Field af = FunctionRepositoryDispatcherService.class.getDeclaredField("activeFunctions");
        af.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> activeFunctions = (java.util.Set<String>) af.get(null);
        activeFunctions.add("fn-x");
        // clear prior readiness for fn-x so processor 42L is genuinely new
        java.lang.reflect.Field fr = FunctionRepositoryDispatcherService.class.getDeclaredField("functionReadiness");
        fr.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, ?> functionReadiness = (java.util.Map<String, ?>) fr.get(null);
        functionReadiness.remove("fn-x");
        FunctionRepositoryRequestParams p = new FunctionRepositoryRequestParams();
        p.processorId = 42L;
        p.functionCodes = List.of("fn-x");
        String data = FunctionRepositoryRequestParamsUtils.UTILS.toString(p);

        svc.processRequest(data, "127.0.0.1");

        boolean hasWs = captured.stream().anyMatch(e -> e instanceof NewWebsocketEvent);
        boolean hasFind = captured.stream().anyMatch(e -> e instanceof FindUnassignedTasksAndRegisterInQueueEvent);
        assertThat(hasWs).as("NewWebsocketEvent published on first-time function readiness").isTrue();
        assertThat(hasFind).as("FindUnassignedTasksAndRegisterInQueueEvent published").isTrue();

        // Second call with same (fn, processor) — already ready — must NOT re-publish
        captured.clear();
        svc.processRequest(data, "127.0.0.1");
        assertThat(captured).as("no events on repeat call with already-ready function/processor").isEmpty();
    }
}
