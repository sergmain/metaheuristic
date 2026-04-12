package ai.metaheuristic.ai.dispatcher.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for rate-limited re-notify decision for unclaimed assignable tasks.
 * Bug: when a WS notification is dropped or races with processor polling, an assignable
 * task sits in TaskQueue with state=NONE, !assigned, and no processor claims it.
 * Without this re-notify, the task stalls forever.
 */
public class TaskProviderUtilsDecideRenotifyTest {

    @Test
    public void test_noUnclaimed_returns_lastUnchanged() {
        long last = 1000L;
        long result = TaskProviderUtils.decideRenotifyMills(false, 9999L, last, 5000L);
        assertThat(result).isEqualTo(last);
    }

    @Test
    public void test_unclaimed_pastInterval_returnsNow_callerShouldFire() {
        long last = 1000L;
        long now = 7000L; // 6s > 5s interval
        long result = TaskProviderUtils.decideRenotifyMills(true, now, last, 5000L);
        assertThat(result).isEqualTo(now);
        assertThat(result).isNotEqualTo(last); // caller detects change => fires event
    }

    @Test
    public void test_unclaimed_withinInterval_returnsLastUnchanged_noFire() {
        long last = 1000L;
        long now = 3000L; // only 2s since last, within 5s interval
        long result = TaskProviderUtils.decideRenotifyMills(true, now, last, 5000L);
        assertThat(result).isEqualTo(last); // no change => caller does NOT fire
    }

    @Test
    public void test_unclaimed_atExactInterval_returnsLastUnchanged() {
        // boundary: now - last == interval (not strictly greater) => do not fire yet
        long last = 1000L;
        long now = 6000L;
        long result = TaskProviderUtils.decideRenotifyMills(true, now, last, 5000L);
        assertThat(result).isEqualTo(last);
    }

    @Test
    public void test_firstCall_fromZero_fires() {
        // initial state: last=0. first scan with unclaimed should fire.
        long result = TaskProviderUtils.decideRenotifyMills(true, 10_000L, 0L, 5000L);
        assertThat(result).isEqualTo(10_000L);
    }
}
