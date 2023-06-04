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

package ai.metaheuristic.ai.dispatcher.test.tx;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManager;

/**
 * @author Serge
 * Date: 9/28/2020
 * Time: 11:55 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TxTestingService {

    private static final String AAA = "AAA";
    private static final String AAA2 = AAA+AAA;
    private final TaskRepository taskRepository;
    private final TxTesting1Service txTesting1Service;
    private final ExecContextCache execContextCache;
    private final EntityManager em;

    @Transactional
    public TaskImpl create(Long execContextId, String params) {
        TaskImpl t = new TaskImpl();
        t.execContextId = execContextId;
        t.execState = EnumsApi.TaskExecState.NONE.value;
        t.setParams(params);

        return taskRepository.save(t);
    }

    @Transactional
    public TaskImpl update(Long taskId, String params) {
        TaskImpl t = taskRepository.findById(taskId).orElseThrow(() -> new IllegalStateException("Task not found"));
        t.setParams(params);
        return taskRepository.save(t);
    }

    @Transactional
    public String updateWithSyncSingle(Long execContextId, Long taskId) {
            TaskImpl t = taskRepository.findById(taskId).orElseThrow(() -> new IllegalStateException("Task not found"));

            t.setParams(AAA);
            taskRepository.save(t);
            TaskImpl t1 = taskRepository.findById(taskId).orElseThrow(() -> new IllegalStateException("Task not found"));
            if (!AAA.equals(t1.getParams())) {
                throw new IllegalStateException("(!'aaa'.equals(t1.params)) ");
            }

            return AAA;
    }

    @Transactional
    public String updateSingle(Long execContextId, Long taskId) {
        TaskImpl t = taskRepository.findById(taskId).orElseThrow(() -> new IllegalStateException("Task not found"));

        t.setParams(AAA);
        taskRepository.save(t);
        TaskImpl t1 = taskRepository.findById(taskId).orElseThrow(() -> new IllegalStateException("Task not found"));
        if (!AAA.equals(t1.getParams())) {
            throw new IllegalStateException("(!'aaa'.equals(t1.params)) ");
        }
        return AAA;
    }

    @Transactional
    public String updateWithSyncDouble(Long execContextId, Long taskId) {
            TaskImpl t = taskRepository.findById(taskId)
                    .orElseThrow(() -> new IllegalStateException("Task not found"));

            t.setParams(AAA);
            taskRepository.save(t);
            TaskImpl t1 = taskRepository.findById(taskId).orElseThrow(() -> new IllegalStateException("Task not found"));
            if (!AAA.equals(t1.getParams())) {
                throw new IllegalStateException("(!AAA.equals(t1.params)) ");
            }

            t1.setParams(AAA2);
            taskRepository.save(t1);

            TaskImpl t2 = taskRepository.findById(taskId).orElseThrow(() -> new IllegalStateException("Task not found"));
            if (!(AAA2.equals(t2.getParams()))) {
                throw new IllegalStateException("(!AAA2.equals(t1.params)) ");
            }
            return AAA2;
    }

    @Transactional
    public String updateDouble(Long execContextId, Long taskId) {
        TaskImpl t = taskRepository.findById(taskId).orElseThrow(() -> new IllegalStateException("Task not found"));

        t.setParams(AAA);
        taskRepository.save(t);
        TaskImpl t1 = taskRepository.findById(taskId).orElseThrow(() -> new IllegalStateException("Task not found"));
        if (!AAA.equals(t1.getParams())) {
            throw new IllegalStateException("(!'aaa'.equals(t1.params)) ");
        }
        t1.setParams(AAA2);
        taskRepository.save(t1);

        TaskImpl t2 = taskRepository.findById(taskId).orElseThrow(() -> new IllegalStateException("Task not found"));
        if (!(AAA2.equals(t2.getParams()))) {
            throw new IllegalStateException("(!AAA2.equals(t1.params)) ");
        }
        return AAA2;
    }


    @Transactional
    public TaskImpl createTask(Long execContextId, String params) {
        TaskImpl t = new TaskImpl();
        t.execContextId = execContextId;
        t.execState = EnumsApi.TaskExecState.NONE.value;
        t.setParams(params);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionStatus status = TransactionAspectSupport.currentTransactionStatus();
        }

        return taskRepository.save(t);
    }


    @Transactional
    public void twoLevelTwoTx(Long id, String newParam, boolean throwException) {
        TaskImpl t1 = taskRepository.findById(id).orElseThrow(() -> new IllegalStateException("Task not found"));
        t1.setParams(newParam);
        try {
            txTesting1Service.forException(throwException);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public void twoLevelOneTx(Long id, String newParam, boolean throwException) {
        TaskImpl t1 = taskRepository.findById(id).orElseThrow(() -> new IllegalStateException("Task not found"));
        t1.setParams(newParam);
        forException(throwException);
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void forException(boolean throwException) {
        if (throwException) {
            throw new RuntimeException();
        }
    }

    @Transactional
    public void oneLevelTx(Long id, String newParam, boolean throwException) {
        TaskImpl t1 = taskRepository.findById(id).orElseThrow(() -> new IllegalStateException("Task not found"));
        t1.setParams(newParam);
        if (throwException) {
            throw new RuntimeException();
        }
    }

    @Transactional
    public void oneLevelTxChecked(Long id, String newParam) throws Exception {
        TaskImpl t1 = taskRepository.findById(id).orElseThrow(() -> new IllegalStateException("Task not found"));
        t1.setParams(newParam);
        throw new Exception();
    }

    @Transactional
    public void oneLevelTxEmpty(Long id){
        TaskImpl t1 = taskRepository.findById(id).orElseThrow(() -> new IllegalStateException("Task not found"));
    }

    @Transactional
    public void testDetachedInTx(Long execContextId) {
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (em.contains(ec)) {
            throw new RuntimeException();
        }
    }

    @Transactional
    public void testDetachedInTxQueryNewTx(Long execContextId) {
        ExecContextImpl ec = execContextCache.findByIdWithNewTx(execContextId);
        if (em.contains(ec)) {
            throw new RuntimeException();
        }
    }

    @Transactional
    public void testDetachedInDetachManually(Long execContextId) {
        ExecContextImpl ec = execContextCache.findById(execContextId, true);
        if (em.contains(ec)) {
            throw new RuntimeException();
        }
    }
}
