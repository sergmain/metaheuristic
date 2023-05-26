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
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 9/28/2020
 * Time: 11:44 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TxRollbackTest extends PreparingSourceCode {

    @Autowired private TxTestingService txTestingService;
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

        System.out.println("### create task");
        TaskImpl task = txTestingService.create(getExecContextForTest().id, "BBB");

        assertNotNull(task.id);
        assertNotNull(task.version);
        assertEquals("BBB", task.params);

        // =====================

        String newParam = StrUtils.incCopyNumber(task.params);
        try {
            System.out.println("### oneLevelTx(), exception: false");
            txTestingService.oneLevelTx(task.id, newParam, false);
        }
        catch (Exception e) {
            //
        }
        // result - data was committed
        TaskImpl t1 = taskRepository.findById(task.id).orElseThrow(() -> new IllegalStateException("Task not found"));
        assertEquals(newParam, t1.params);

        // =====================

        String newParam2 = StrUtils.incCopyNumber(t1.params);
        assertNotEquals(newParam, newParam2);
        int version2 = t1.version;
        try {
            System.out.println("### oneLevelTx(), exception: true");
            txTestingService.oneLevelTx(task.id, newParam2, true);
        }
        catch (Exception e) {
            //
        }
        // result - data wasn't committed
        TaskImpl t2 = taskRepository.findById(task.id).orElseThrow(() -> new IllegalStateException("Task not found"));
        assertEquals(version2, t2.version);
        assertNotEquals(newParam2, t2.params);

        // =====================

        String newParam3 = StrUtils.incCopyNumber(t2.params);
        int version3 = t2.version;
        try {
            System.out.println("### oneLevelTxChecked()");
            txTestingService.oneLevelTxChecked(task.id, newParam3);
        }
        catch (Exception e) {
            //
        }
        // result - data was committed
        TaskImpl t3 = taskRepository.findById(task.id).orElseThrow(() -> new IllegalStateException("Task not found"));
        assertNotEquals(version3, t3.version);
        assertEquals(newParam3, t3.params);

        // =====================

        int version4 = t3.version;
        try {
            System.out.println("### oneLevelTxEmpty()");
            txTestingService.oneLevelTxEmpty(task.id);
        }
        catch (Exception e) {
            //
        }
        // result - data wasn't committed
        TaskImpl t4 = taskRepository.findById(task.id).orElseThrow(() -> new IllegalStateException("Task not found"));
        assertEquals(version4, t4.version);


        // =====================

        System.out.println("### The end.");
    }

    @Test
    public void test_two_levels_one_tx() {
        ExecContextCreatorService.ExecContextCreationResult r = preparingSourceCodeService.createExecContextForTest(preparingSourceCodeData);
        assertNotNull(r.execContext);
        setExecContextForTest(r.execContext);

        System.out.println("### create task");
        TaskImpl task = txTestingService.create(getExecContextForTest().id, "BBB");

        assertNotNull(task.id);
        assertNotNull(task.version);
        assertEquals("BBB", task.params);

        // =====================

        String newParam = StrUtils.incCopyNumber(task.params);
        try {
            System.out.println("### oneLevelTx(), exception: false");
            txTestingService.twoLevelOneTx(task.id, newParam, false);
        }
        catch (Exception e) {
            //
        }
        // result - data was committed
        TaskImpl t1 = taskRepository.findById(task.id).orElseThrow(() -> new IllegalStateException("Task not found"));
        assertEquals(newParam, t1.params);

        // =====================

        String newParam2 = StrUtils.incCopyNumber(t1.params);
        assertNotEquals(newParam, newParam2);
        int version2 = t1.version;
        try {
            System.out.println("### oneLevelTx(), exception: true");
            txTestingService.twoLevelOneTx(task.id, newParam2, true);
        }
        catch (Exception e) {
            //
        }
        // result - data wasn't committed
        TaskImpl t2 = taskRepository.findById(task.id).orElseThrow(() -> new IllegalStateException("Task not found"));
        assertEquals(version2, t2.version);
        assertNotEquals(newParam2, t2.params);

        // =====================

        System.out.println("### The end.");
    }

    @Test
    public void test_two_levels_two_tx() {
        ExecContextCreatorService.ExecContextCreationResult r = preparingSourceCodeService.createExecContextForTest(preparingSourceCodeData);
        assertNotNull(r.execContext);
        setExecContextForTest(r.execContext);

        System.out.println("### create task");
        TaskImpl task = txTestingService.create(getExecContextForTest().id, "BBB");

        assertNotNull(task.id);
        assertNotNull(task.version);
        assertEquals("BBB", task.params);

        // =====================

        String newParam = StrUtils.incCopyNumber(task.params);
        try {
            System.out.println("### oneLevelTx(), exception: false");
            txTestingService.twoLevelTwoTx(task.id, newParam, false);
        }
        catch (Exception e) {
            //
        }
        // result - data was committed
        TaskImpl t1 = taskRepository.findById(task.id).orElseThrow(() -> new IllegalStateException("Task not found"));
        assertEquals(newParam, t1.params);

        // =====================

        String newParam2 = StrUtils.incCopyNumber(t1.params);
        assertNotEquals(newParam, newParam2);
        int version2 = t1.version;
        try {
            System.out.println("### oneLevelTx(), exception: true");
            txTestingService.twoLevelTwoTx(task.id, newParam2, true);
        }
        catch (Exception e) {
            //
        }
        // result - data wasn't committed
        TaskImpl t2 = taskRepository.findById(task.id).orElseThrow(() -> new IllegalStateException("Task not found"));
        assertEquals(version2, t2.version);
        assertNotEquals(newParam2, t2.params);

        // =====================

        System.out.println("### The end.");
    }

}
