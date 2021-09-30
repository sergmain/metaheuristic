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
package ai.metaheuristic.ai.dispatcher.commons;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.batch.BatchCache;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.cache.CacheService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.*;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ArtifactCleanerAtDispatcher {

    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextRepository execContextRepository;
    private final SourceCodeCache sourceCodeCache;
    private final BatchRepository batchRepository;
    private final CompanyRepository companyRepository;
    private final BatchTopLevelService batchTopLevelService;
    private final BatchCache batchCache;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final TaskTransactionalService taskTransactionalService;
    private final VariableService variableService;
    private final VariableRepository variableRepository;
    private final FunctionRepository functionRepository;
    private final CacheProcessRepository cacheProcessRepository;
    private final CacheService cacheService;
    private final ExecContextService execContextService;

    public void fixedDelay() {
        TxUtils.checkTxNotExists();

        // do not change the order of calling
        deleteOrphanBatches();
        deleteOrphanExecContexts();
        deleteOrphanTasks();
        deleteOrphanVariables();
        deleteOrphanCacheData();
    }

    private void deleteOrphanBatches() {
        List<Long> execContextIds = execContextRepository.findAllIds();
        List<Long> companyUniqueIds = companyRepository.findAllUniqueIds();
        Set<Long> forDeletion = new HashSet<>();
        List<Long> ids = batchRepository.findAllIds();
        for (Long id : ids) {
            Batch b = batchCache.findById(id);
            if (b==null) {
                continue;
            }
            if (!execContextIds.contains(b.execContextId) || !companyUniqueIds.contains(b.companyId)) {
                forDeletion.add(b.id);
            }
        }
        batchTopLevelService.deleteOrphanBatches(forDeletion);
    }

    private void deleteOrphanExecContexts() {
        Set<Long> forDeletion = new HashSet<>();
        List<Object[]> objs = execContextRepository.findAllExecContextIdWithSourceCodeId();
        for (Object[] obj : objs) {
            long sourceCodeId = ((Number)obj[1]).longValue();
            if (sourceCodeCache.findById(sourceCodeId)==null) {
                long execContextId = ((Number)obj[0]).longValue();
                forDeletion.add(execContextId);
            }
        }
        execContextTopLevelService.deleteOrphanExecContexts(forDeletion);
    }

    private void deleteOrphanTasks() {
        TxUtils.checkTxNotExists();

        List<Long> taskExecContextIds = taskRepository.getAllExecContextIds();
        List<Long> execContextIds = execContextRepository.findAllIds();

        List<Long> orphanExecContextIds = taskExecContextIds.stream()
                .filter(o->!execContextIds.contains(o)).collect(Collectors.toList());

        for (Long execContextId : orphanExecContextIds) {
            if (execContextCache.findById(execContextId)!=null) {
                log.warn("execContextId #{} still here", execContextId);
                Long id = execContextRepository.findIdById(execContextId);
                if (id != null) {
                    continue;
                }
                log.warn("execContextId #{} was deleted in db, clean up the cache", execContextId);
                execContextService.deleteExecContextFromCache(execContextId);
            }

            List<Long> ids;
            while (!(ids = taskRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 10);
                for (List<Long> page : pages) {
                    log.info("Found orphan task, execContextId: #{}, tasks #{}", execContextId, page);
                    execContextSyncService.getWithSyncNullable(execContextId, () -> taskTransactionalService.deleteOrphanTasks(page));
                }
            }
        }
    }

    private void deleteOrphanVariables() {
        List<Long> variableExecContextIds = variableRepository.getAllExecContextIds();
        List<Long> execContextIds = execContextRepository.findAllIds();

        List<Long> orphanExecContextIds = variableExecContextIds.stream()
                .filter(o->!execContextIds.contains(o)).collect(Collectors.toList());

        for (Long execContextId : orphanExecContextIds) {
            if (execContextCache.findById(execContextId)!=null) {
                log.warn("execContextId #{} wasn't deleted, actually", execContextId);
                continue;
            }

            List<Long> ids;
            while (!(ids = variableRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 5);
                for (List<Long> page : pages) {
                    if (page.isEmpty()) {
                        continue;
                    }
                    log.info("Found orphan variables, execContextId: #{}, variables #{}", execContextId, page);
                    execContextSyncService.getWithSyncNullable(execContextId, () -> variableService.deleteOrphanVariables(page));
                }
            }
        }
    }

    private void deleteOrphanCacheData() {
        List<String> funcCodes = functionRepository.findAllFunctionCodes();
        Set<String> currFuncCodes = cacheProcessRepository.findAllFunctionCodes();

        List<String> missingCodes = currFuncCodes.stream().filter(currFuncCode -> !funcCodes.contains(currFuncCode)).collect(Collectors.toList());

        for (String funcCode : missingCodes) {
            List<Long> ids;
            while (!(ids = cacheProcessRepository.findByFunctionCode(Consts.PAGE_REQUEST_100_REC, funcCode)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 5);
                for (List<Long> page : pages) {
                    if (page.isEmpty()) {
                        continue;
                    }
                    log.info("Found orphan cache entries, funcCode: #{}, cacheProcessIds #{}", funcCode, page);
                    cacheService.deleteCacheProcesses(page);
                    for (Long cacheProcessId : page) {
                        cacheService.deleteCacheVariable(cacheProcessId);
                    }
                }
            }
        }
    }
}
