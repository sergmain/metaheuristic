/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.tx;

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxTestingService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxTestingTopLevelService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 9/28/2020
 * Time: 11:44 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles({"dispatcher", "mysql"})
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestTx extends PreparingSourceCode {

    @Autowired private TxTestingService txTestingService;
    @Autowired private TxTestingTopLevelService txTestingTopLevelService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void testSingleThread() {
        ExecContextCreatorService.ExecContextCreationResult r = preparingSourceCodeService.createExecContextForTest(preparingSourceCodeData);
        assertNotNull(r.execContext);
        setExecContextForTest(r.execContext);

        TaskImpl task = txTestingService.create(getExecContextForTest().id, "BBB");

        assertNotNull(task.id);
        assertNotNull(task.version);
        assertEquals("BBB", task.getParams());

        // == txTestingTopLevelService

        String s = txTestingTopLevelService.updateWithSyncSingle(getExecContextForTest().id, task.id);
        assertEquals("AAA", s);

        TaskImpl task1 = txTestingService.update(task.id, "BBB");

        assertNotNull(task1.id);
        assertNotNull(task1.version);
        assertEquals("BBB", task1.getParams());
        assertTrue((int)task1.version>task.version);
        TaskImpl t1 = taskRepository.findByIdReadOnly(task.id);
        assertNotNull(t1);
        assertEquals(t1, task1);

        ////

        s = txTestingTopLevelService.updateWithSyncDouble(getExecContextForTest().id, task.id);
        assertEquals("AAAAAA", s);

        TaskImpl task2 = txTestingService.update(task.id, "BBB");

        assertNotNull(task2.id);
        assertNotNull(task2.version);
        assertEquals("BBB", task2.getParams());
        assertTrue((int)task2.version>task1.version);
        TaskImpl t2 = taskRepository.findById(task.id).orElseThrow(() -> new IllegalStateException("Task not found"));
        assertEquals(t2, task2);


        // == txTestingService

        s = txTestingService.updateSingle(getExecContextForTest().id, task.id);
        assertEquals("AAA", s);

        TaskImpl task3 = txTestingService.update(task.id, "BBB");

        assertNotNull(task3.id);
        assertNotNull(task3.version);
        assertEquals("BBB", task3.getParams());
        assertTrue((int)task3.version>task2.version);
        TaskImpl t3 = taskRepository.findById(task.id).orElseThrow(() -> new IllegalStateException("Task not found"));
        assertEquals(t3, task3);


        ////

        s = txTestingService.updateDouble(getExecContextForTest().id, task.id);
        assertEquals("AAAAAA", s);

        TaskImpl task4 = txTestingService.update(task.id, "BBB");

        assertNotNull(task4.id);
        assertNotNull(task4.version);
        assertEquals("BBB", task4.getParams());
        assertTrue((int)task4.version>task3.version);
        TaskImpl t4 = taskRepository.findById(task.id).orElseThrow(() -> new IllegalStateException("Task not found"));
        assertEquals(t4, task4);

        ////
    }


    @Test
    public void testMultiThreadTopLevelService() throws InterruptedException {
        ExecContextCreatorService.ExecContextCreationResult r = preparingSourceCodeService.createExecContextForTest(preparingSourceCodeData);
        assertNotNull(r.execContext);
        setExecContextForTest(r.execContext);

        TaskImpl task = txTestingService.create(getExecContextForTest().id, "BBB");

        assertNotNull(task.id);
        assertNotNull(task.version);
        assertEquals("BBB", task.getParams());

        AtomicBoolean t1 = new AtomicBoolean();
        AtomicBoolean t2 = new AtomicBoolean();
        AtomicBoolean t3 = new AtomicBoolean();
        AtomicBoolean t4 = new AtomicBoolean();
        AtomicBoolean error = new AtomicBoolean();
        new Thread(()-> {
            try {
                testTopLevelService(task.id);
            } catch (Throwable e) {
                error.set(true);
                e.printStackTrace();
            }
            t1.set(true);
        }, "t1"
        ).start();
        new Thread(()-> {
            try {
                testTopLevelService(task.id);
            } catch (Throwable e) {
                error.set(true);
                e.printStackTrace();
            }
            t2.set(true);
        }, "t2"
        ).start();
        new Thread(()-> {
            try {
                testTopLevelService(task.id);
            } catch (Throwable e) {
                error.set(true);
                e.printStackTrace();
            }
            t3.set(true);
        }, "t3"
        ).start();
        new Thread(()-> {
            try {
                testTopLevelService(task.id);
            } catch (Throwable e) {
                error.set(true);
                e.printStackTrace();
            }
            t4.set(true);
        }, "t4"
        ).start();

        long mills = System.currentTimeMillis();

        while( true ) {
            Thread.sleep(1_000);
            if (System.currentTimeMillis() - mills >60_000) {
                throw new IllegalStateException("Too long");
            }
            if (t1.get() && t2.get() && t3.get() && t4.get()) {
                break;
            }
        }
        assertFalse(error.get());
    }

    private void testTopLevelService(Long taskId) {
        // == txTestingTopLevelService

        String s = txTestingTopLevelService.updateWithSyncSingle(getExecContextForTest().id, taskId);
        assertEquals("AAA", s);

        ////

        s = txTestingTopLevelService.updateWithSyncDouble(getExecContextForTest().id, taskId);
        assertEquals("AAAAAA", s);

    }

    @Test
    public void testMultiThreadService() throws InterruptedException {
        ExecContextCreatorService.ExecContextCreationResult r = preparingSourceCodeService.createExecContextForTest(preparingSourceCodeData);
        assertNotNull(r.execContext);
        setExecContextForTest(r.execContext);

        TaskImpl task = txTestingService.create(getExecContextForTest().id, "BBB");

        assertNotNull(task.id);
        assertNotNull(task.version);
        assertEquals("BBB", task.getParams());

        AtomicBoolean t1 = new AtomicBoolean();
        AtomicBoolean t2 = new AtomicBoolean();
        AtomicBoolean t3 = new AtomicBoolean();
        AtomicBoolean t4 = new AtomicBoolean();
        AtomicBoolean error = new AtomicBoolean();
        new Thread(()-> {
            try {
                testService(task.id);
            } catch (Throwable e) {
                error.set(true);
                e.printStackTrace();
            }
            t1.set(true);
        }, "t1"
        ).start();
        new Thread(()-> {
            try {
                testService(task.id);
            } catch (Throwable e) {
                error.set(true);
                e.printStackTrace();
            }
            t2.set(true);
        }, "t2"
        ).start();
        new Thread(()-> {
            try {
                testService(task.id);
            } catch (Throwable e) {
                error.set(true);
                e.printStackTrace();
            }
            t3.set(true);
        }, "t3"
        ).start();
        new Thread(()-> {
            try {
                testService(task.id);
            } catch (Throwable e) {
                error.set(true);
                e.printStackTrace();
            }
            t4.set(true);
        }, "t4"
        ).start();

        long mills = System.currentTimeMillis();

        while( true ) {
            Thread.sleep(1_000);
            if (System.currentTimeMillis() - mills >60_000) {
                throw new IllegalStateException("Too long");
            }
            if (t1.get() && t2.get() && t3.get() && t4.get()) {
                break;
            }
        }
        assertFalse(error.get());
    }

    private void testService(Long taskId) {
        ////
        String s = ExecContextSyncService.getWithSync(getExecContextForTest().id,
                () -> txTestingService.updateWithSyncSingle(getExecContextForTest().id, taskId));

        assertEquals("AAA", s);

        ////

        s = ExecContextSyncService.getWithSync(getExecContextForTest().id,
                () -> txTestingService.updateWithSyncDouble(getExecContextForTest().id, taskId));
        assertEquals("AAAAAA", s);


    }

}
