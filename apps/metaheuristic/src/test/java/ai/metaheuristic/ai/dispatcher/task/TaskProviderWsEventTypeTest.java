package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.event.events.NewWebsocketEvent;
import ai.metaheuristic.ai.dispatcher.event.events.NewWebsocketTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterization test for the WS-mode stall bug where Task stays in NONE
 * because NewWebsocketEvent fires before the enclosing transaction commits.
 */
public class TaskProviderWsEventTypeTest {

/*
    @Test
    public void test_setTaskExecStateInQueue_publishes_txBoundEvent() {
        List<Object> captured = new ArrayList<>();
        ApplicationEventPublisher publisher = new ApplicationEventPublisher() {
            @Override public void publishEvent(Object event) { captured.add(event); }
        };

        ExecContextCache execContextCache = mock(ExecContextCache.class);
        ExecContextImpl ec = new ExecContextImpl();
        ec.id = 55L;
        when(execContextCache.findById(55L, true)).thenReturn(ec);

        TaskProviderTopLevelService svc = new TaskProviderTopLevelService(
            null, null, null, null, null, null, null, null, execContextCache, publisher, null, null);

        svc.setTaskExecStateInQueue(55L, 3947L, EnumsApi.TaskExecState.NONE);

        List<Object> wsEvents = captured.stream()
            .filter(e -> e instanceof NewWebsocketEvent || e instanceof NewWebsocketTxEvent)
            .toList();
        assertThat(wsEvents).hasSize(1);
        // Green-1 (buggy current behavior): raw NewWebsocketEvent fires pre-commit.
        // Red (desired): should be NewWebsocketTxEvent so it fires AFTER_COMMIT.
        assertThat(wsEvents.get(0)).isInstanceOf(NewWebsocketTxEvent.class);
    }
*/
}
