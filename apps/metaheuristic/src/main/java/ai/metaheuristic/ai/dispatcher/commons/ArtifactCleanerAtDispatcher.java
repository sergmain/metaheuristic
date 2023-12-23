/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
package ai.metaheuristic.ai.dispatcher.commons;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.cache.CacheTxService;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTxService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionRegisterService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorSyncService;
import ai.metaheuristic.ai.dispatcher.processor_core.ProcessorCoreTxService;
import ai.metaheuristic.ai.dispatcher.repositories.*;
import ai.metaheuristic.ai.dispatcher.task.TaskQueueService;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ArtifactCleanerAtDispatcher {

    private final Globals globals;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextRepository execContextRepository;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ExecContextVariableStateRepository execContextVariableStateRepository;
    private final SourceCodeRepository sourceCodeRepository;
    private final BatchRepository batchRepository;
    private final CompanyRepository companyRepository;
    private final BatchTopLevelService batchTopLevelService;
    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final TaskTransactionalService taskTransactionalService;
    private final VariableTxService variableService;
    private final VariableRepository variableRepository;
    private final FunctionRepository functionRepository;
    private final CacheProcessRepository cacheProcessRepository;
    private final CacheTxService cacheService;
    private final ExecContextTxService execContextTxService;
    private final DispatcherEventRepository dispatcherEventRepository;
    private final FunctionDataRepository functionDataRepository;
    private final ProcessorRepository processorRepository;
    private final ProcessorCoreTxService processorCoreService;
    private final ProcessorCoreRepository processorCoreRepository;
    private final InternalFunctionRegisterService internalFunctionRegisterService;

    private static final AtomicInteger busy = new AtomicInteger(0);
    private static long mills = 0L;

    private static boolean isBusy() {
        final boolean b = busy.get() > 0;
        if (b) {
            return true;
        }
        if (System.currentTimeMillis() - mills > 30_000) {
            mills = System.currentTimeMillis();
            return !TaskQueueService.isQueueEmptyWithSync();
        }
        return false;
    }

    public static void setBusy() {
        ArtifactCleanerAtDispatcher.busy.incrementAndGet();
    }

    public static void notBusy() {
        int curr = ArtifactCleanerAtDispatcher.busy.decrementAndGet();
        if (curr<0) {
            throw new IllegalStateException("(curr<0)");
        }
    }

    public void fixedDelay() {
        TxUtils.checkTxNotExists();

        deleteOrphanExecContexts();

        if (isBusy()) {
            return;
        }

        // do not change the order of calling
        deleteOrphanAndObsoletedBatches();
        deleteOrphanTasks();
        deleteOrphanVariables();
        deleteOrphanCacheData();
        deleteObsoleteEvents();
        deleteObsoleteFunctionData();
        deleteOrphanCores();
    }

    private void deleteOrphanCores() {
        log.info("510.030 start deleteOrphanCores()");
        List<Long> coresIds = processorCoreRepository.getAllProcessorIds();
        List<Long> processorIds = processorRepository.findAllIds();

        //noinspection SimplifyStreamApiCallChains
        List<Long> orphanProcessorIds = coresIds.stream()
                .filter(o->!processorIds.contains(o)).collect(Collectors.toList());

        for (Long processorId : orphanProcessorIds) {
            if (processorRepository.findById(processorId).isPresent()) {
                log.warn("processorId #{} wasn't deleted, actually", processorId);
                continue;
            }

            List<Long> ids;
            while (!(ids = processorCoreRepository.findIdsByProcessorId(Consts.PAGE_REQUEST_100_REC, processorId)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 10);
                for (List<Long> page : pages) {
                    if (isBusy()) {
                        return;
                    }
                    if (page.isEmpty()) {
                        continue;
                    }
                    log.info("Found orphan ProcessorCore, processorId: #{}, cores #{}", processorId, page);
                    try {
                        ProcessorSyncService.getWithSyncVoid(processorId, ()->processorCoreService.deleteOrphanProcessorCores(page));
                    }
                    catch (Throwable th) {
                        log.error("510.060 variableService.deleteOrphanVariables("+processorId+")", th);
                    }
                }
            }
        }
    }

    private void deleteObsoleteFunctionData() {
        log.info("510.090 start deleteObsoleteFunctionData()");
        List<String> functionCodesInData = functionDataRepository.findAllFunctionCodes();
        List<String> functionCodes = functionRepository.findAllFunctionCodes();
        for (String functionCode : functionCodesInData) {
            if (!functionCodes.contains(functionCode)) {
                if (isBusy()) {
                    return;
                }
                try {
                    functionDataRepository.deleteByFunctionCode(functionCode);
                }
                catch (Throwable th) {
                    log.warn("510.120 error while deleting obsolete function " + functionCode+", " + th);
                }
            }
        }
    }

    private void deleteObsoleteEvents() {
        log.info("510.150 start deleteObsoleteEvents()");
        final int keepPeriod = globals.dispatcher.getKeepEventsInDb().getDays();
        if (keepPeriod >100000) {
            final String es = "510.180 globals.dispatcher.keepEventsInDb greater than 100000 days, actual: " + keepPeriod;
            log.error(es);
            throw new RuntimeException(es);
        }

        LocalDate today = LocalDate.now();
        LocalDate keepStartDate = today.minusDays(keepPeriod);
        int period = DispatcherEventService.getPeriod(keepStartDate);

        List<Long> periodsForDelete = dispatcherEventRepository.getPeriodIdsBefore(period);
        List<List<Long>> pages = CollectionUtils.parseAsPages(periodsForDelete, 20);
        for (List<Long> page : pages) {
            if (isBusy()) {
                return;
            }
            if (page.isEmpty()) {
                continue;
            }
            log.info("510.210 Found obsolete events #{}", page);
            try {
                dispatcherEventRepository.deleteAllByIdIn(page);
            }
            catch (Throwable th) {
                log.error("510.240 dispatcherEventRepository.deleteAllByIdIn("+page+")", th);
            }
        }
    }

    private void deleteOrphanAndObsoletedBatches() {
        log.info("510.270 start deleteOrphanAndObsoletedBatches()");
        List<Long> execContextIds = execContextRepository.findAllIds();
        List<Long> companyUniqueIds = companyRepository.findAllUniqueIds();
        Set<Long> forDeletion = new HashSet<>();
        List<Object[]> batches = batchRepository.findAllIdAndCreatedOnAndDeleted();
        for (Object[] obj : batches) {
            if (isBusy()) {
                return;
            }
            long batchId = ((Number)obj[0]).longValue();
            long createdOn = ((Number)obj[1]).longValue();
            boolean deleted = Boolean.TRUE.equals(obj[2]);

            if (deleted && System.currentTimeMillis() > createdOn + globals.dispatcher.timeout.batchDeletion.toMillis()) {
                forDeletion.add(batchId);
            }
            else {
                Batch b = batchRepository.findByIdWithNull(batchId);
                if (b == null) {
                    continue;
                }
                if (!execContextIds.contains(b.execContextId) || !companyUniqueIds.contains(b.companyId)) {
                    forDeletion.add(b.id);
                }
            }
        }
        batchTopLevelService.deleteOrphanOrObsoletedBatches(forDeletion);
    }

    private void deleteOrphanExecContexts() {
        log.info("510.300 start deleteOrphanExecContexts()");
        List<Long> execContextIds = execContextRepository.findAllIds();
        if (execContextIds.isEmpty()) {
            return;
        }

        // execContext mus be deleted without checking isBusy() because we need to terminate all running tasks
        // all running tasks at processor will be terminated only if a related ExecContext doesn't exist
        deleteOrphanExecContexts(execContextIds);

        // this operation isn't complex so don't need to use isBusy()
        markTasksAsFinishedForFinishedExecContext();

        if (isBusy()) {
            return;
        }
        deleteOrphanExecContextGraph(execContextIds);
        if (isBusy()) {
            return;
        }
        deleteOrphanExecContextTaskState(execContextIds);
        if (isBusy()) {
            return;
        }
        deleteOrphanExecContextVariableState(execContextIds);
    }

    private void markTasksAsFinishedForFinishedExecContext() {
        List<Long> forUpdating = taskRepository.getUnfinishedTaskForFinishedExecContext();
        List<List<Long>> pages = CollectionUtils.parseAsPages(forUpdating, 5);
        for (List<Long> page : pages) {
            if (page.isEmpty()) {
                continue;
            }
            log.info("Found tasks with lost state, tasks #{}", page);
            try {
                taskRepository.updateTaskAsFinished(page);
            }
            catch (Throwable th) {
                log.error("510.330 taskRepository.updateTaskAsFinished("+page+")", th);
            }
        }
    }

    private void deleteOrphanExecContexts(List<Long> execContextIds) {
        log.info("510.360 start deleteOrphanExecContexts(execContextIds)");
        Set<Long> forDeletion = new HashSet<>();
        List<Long> sourceCodeIds = sourceCodeRepository.findAllAsIds();
        for (Long execContextId : execContextIds) {
            ExecContextImpl ec = execContextRepository.findByIdNullable(execContextId);
            if (ec==null) {
                continue;
            }
            if ((ec.rootExecContextId!=null && !execContextIds.contains(ec.rootExecContextId)) || !sourceCodeIds.contains(ec.sourceCodeId)) {
                forDeletion.add(execContextId);
            }
        }
        execContextTopLevelService.deleteOrphanExecContexts(forDeletion);
    }

    private void deleteOrphanExecContextGraph(List<Long> execContextIds) {
        log.info("510.420 start deleteOrphanExecContextGraph(execContextIds)");
        Set<Long> execContextGraphIds = execContextRepository.findExecContextGraphIds(execContextIds);
        List<Long> allExecContextGraphIds = execContextGraphRepository.findAllIds();
        for (Long allExecContextGraphId : allExecContextGraphIds) {
            if (!execContextGraphIds.contains(allExecContextGraphId)) {
                ExecContextGraph execContextGraph = execContextGraphRepository.findById(allExecContextGraphId).orElse(null);
                if (execContextGraph==null || execContextGraph.createdOn==null ||
                        execContextGraph.createdOn==0 || (System.currentTimeMillis()-execContextGraph.createdOn) < 3_600_000 ) {
                    continue;
                }
                log.info("510.450 Found orphan ExecContextGraph #{}", allExecContextGraphId);
                try {
                    execContextTxService.deleteOrphanExecContextGraph(allExecContextGraphId);
                }
                catch (Throwable th) {
                    log.warn("510.480 error while deleting ExecContextGraph #" + allExecContextGraphId);
                }
            }
        }
    }

    private void deleteOrphanExecContextTaskState(List<Long> execContextIds) {
        log.info("510.510 start deleteOrphanExecContextTaskState(execContextIds)");
        Set<Long> execContextTaskStateIds = execContextRepository.findExecContextTaskStateIds(execContextIds);

        List<Long> allExecContextTaskStateIds = execContextTaskStateRepository.findAllIds();
        for (Long allExecContextTaskStateId : allExecContextTaskStateIds) {
            if (!execContextTaskStateIds.contains(allExecContextTaskStateId)) {
                ExecContextTaskState execContextTaskState = execContextTaskStateRepository.findById(allExecContextTaskStateId).orElse(null);
                if (execContextTaskState==null || execContextTaskState.createdOn==null ||
                        execContextTaskState.createdOn==0 || (System.currentTimeMillis()-execContextTaskState.createdOn) < 18_000_000 ) {
                    continue;
                }
                log.info("510.540 Found orphan ExecContextTaskState #{}", allExecContextTaskStateId);
                try {
                    execContextTxService.deleteOrphanExecContextTaskState(allExecContextTaskStateId);
                }
                catch (Throwable th) {
                    log.warn("510.570 error while deleting ExecContextTaskState #" + allExecContextTaskStateId);
                }
            }
        }
    }

    private void deleteOrphanExecContextVariableState(List<Long> execContextIds) {
        log.info("510.600 start deleteOrphanExecContextVariableState(execContextIds)");
        Set<Long> execContextVariableStateIds = execContextRepository.findExecContextVariableStateIds(execContextIds);

        List<Long> allExecContextVariableStateIds = execContextVariableStateRepository.findAllIds();
        for (Long allExecContextVariableStateId : allExecContextVariableStateIds) {
            if (!execContextVariableStateIds.contains(allExecContextVariableStateId)) {
                ExecContextVariableState execContextVariableState = execContextVariableStateRepository.findById(allExecContextVariableStateId).orElse(null);
                if (execContextVariableState==null || execContextVariableState.createdOn==null ||
                        execContextVariableState.createdOn==0 || (System.currentTimeMillis()-execContextVariableState.createdOn) < 3_600_000 ) {
                    continue;
                }
                log.info("510.630 Found orphan ExecContextVariableState #{}", allExecContextVariableStateId);
                try {
                    execContextTxService.deleteOrphanExecContextVariableState(allExecContextVariableStateId);
                }
                catch (Throwable th) {
                    log.warn("510.660 error while deleting ExecContextVariableState " + allExecContextVariableStateId);
                }
            }
        }
    }

    private void deleteOrphanTasks() {
        TxUtils.checkTxNotExists();
        log.info("510.690 start deleteOrphanTasks()");

        List<Long> taskExecContextIds = taskRepository.getAllExecContextIds();
        List<Long> execContextIds = execContextRepository.findAllIds();

        //noinspection SimplifyStreamApiCallChains
        List<Long> orphanExecContextIds = taskExecContextIds.stream()
                .filter(o->!execContextIds.contains(o)).collect(Collectors.toList());

        for (Long execContextId : orphanExecContextIds) {
            if (isBusy()) {
                return;
            }
            if (execContextCache.findById(execContextId, true)!=null) {
                log.warn("execContextId #{} still here", execContextId);
                Long id = execContextRepository.findIdById(execContextId);
                if (id != null) {
                    continue;
                }
                log.warn("execContextId #{} was deleted in db, clean up the cache", execContextId);
                try {
                    execContextTxService.deleteExecContextFromCache(execContextId);
                }
                catch (Throwable th) {
                    log.error("510.720 execContextService.deleteExecContextFromCache("+execContextId+")", th);
                }
            }

            List<Long> ids;
            while (!(ids = taskRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 10);
                for (List<Long> page : pages) {
                    if (page.isEmpty() || isBusy()) {
                        return;
                    }
                    log.info("Found orphan task, execContextId: #{}, tasks #{}", execContextId, page);
                    try {
                        taskTransactionalService.deleteOrphanTasks(page);
                    }
                    catch (Throwable th) {
                        log.error("510.750 taskTransactionalService.deleteOrphanTasks("+execContextId+")", th);
                    }
                }
            }
        }
    }

    private void deleteOrphanVariables() {
        log.info("510.780 start deleteOrphanVariables()");
        List<Long> variableExecContextIds = variableRepository.getAllExecContextIds();
        List<Long> execContextIds = execContextRepository.findAllIds();

        //noinspection SimplifyStreamApiCallChains
        List<Long> orphanExecContextIds = variableExecContextIds.stream()
                .filter(o->!execContextIds.contains(o)).collect(Collectors.toList());

        for (Long execContextId : orphanExecContextIds) {
            if (execContextCache.findById(execContextId, true)!=null) {
                log.warn("execContextId #{} wasn't deleted, actually", execContextId);
                continue;
            }

            List<Long> ids;
            while (!(ids = variableRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 10);
                for (List<Long> page : pages) {
                    if (isBusy()) {
                        return;
                    }
                    if (page.isEmpty()) {
                        continue;
                    }
                    log.info("Found orphan variables, execContextId: #{}, variables #{}", execContextId, page);
                    try {
                        variableService.deleteOrphanVariables(page);
                    }
                    catch (Throwable th) {
                        log.error("510.810 variableService.deleteOrphanVariables("+execContextId+")", th);
                    }
                }
            }
        }
    }

    private void deleteOrphanCacheData() {
        log.info("510.840 start deleteOrphanCacheData()");
        List<String> funcCodes = functionRepository.findAllFunctionCodes();
        funcCodes.addAll(internalFunctionRegisterService.getCachableFunctions());

        Set<String> currFuncCodes = cacheProcessRepository.findAllFunctionCodes();

        //noinspection SimplifyStreamApiCallChains
        List<String> missingCodes = currFuncCodes.stream().filter(currFuncCode -> !funcCodes.contains(currFuncCode)).collect(Collectors.toList());

        for (String funcCode : missingCodes) {
            List<Long> ids;
            while (!(ids = cacheProcessRepository.findByFunctionCode(Consts.PAGE_REQUEST_100_REC, funcCode)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 10);
                for (List<Long> page : pages) {
                    if (isBusy()) {
                        return;
                    }
                    if (page.isEmpty()) {
                        continue;
                    }
                    log.info("Found orphan cache entries, funcCode: #{}, cacheProcessIds #{}", funcCode, page);
                    try {
                        cacheService.deleteCacheProcesses(page);
                    }
                    catch (Throwable th) {
                        log.error("510.860 cacheService.deleteCacheProcesses("+page+")", th);
                    }
                    for (Long cacheProcessId : page) {
                        try {
                            cacheService.deleteCacheVariable(cacheProcessId);
                        }
                        catch (Throwable th) {
                            log.error("510.880 cacheService.deleteCacheVariable("+page+")", th);
                        }
                    }
                }
            }
        }
    }
}
