package aiai.ai.station.actors;

import aiai.ai.station.tasks.DownloadResourceTask;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class TestAbstractTaskQueue {

    public static class SimpleClass extends AbstractTaskQueue<DownloadResourceTask> {
    }

    @Test
    public void test() {
        SimpleClass actor = new SimpleClass();

        DownloadResourceTask task = new DownloadResourceTask("resource-id-01", 10, new File("aaa"));

        actor.add(task);
        assertEquals(1, actor.queueSize());

        DownloadResourceTask task1 = new DownloadResourceTask("resource-id-01", 10, new File("bbb"));
        actor.add(task1);
        assertEquals(1, actor.queueSize());

        DownloadResourceTask t1 = actor.poll();
        assertNotNull(t1);

        DownloadResourceTask t2 = actor.poll();
        assertNull(t2);

        DownloadResourceTask task2 = new DownloadResourceTask("resource-id-02", 10, new File("."));
        actor.add(task1);
        actor.add(task2);
        assertEquals(2, actor.queueSize());

        DownloadResourceTask task3 = new DownloadResourceTask("resource-id-02", 11, new File("."));
        actor.add(task3);
        assertEquals(3, actor.queueSize());

    }
}
