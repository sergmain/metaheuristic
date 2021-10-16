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

package ai.metaheuristic.ai.dispatcher.dispatcher_params;

import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.event.DispatcherCacheRemoveSourceCodeEvent;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Serge
 * Date: 7/14/2021
 * Time: 12:15 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class DispatcherParamsTopLevelService {

    public final DispatcherParamsService dispatcherParamsService;
    public final ApplicationEventPublisher eventPublisher;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    @Async
    @EventListener
    public void handleDispatcherCacheRemoveSourceCodeEvent(final DispatcherCacheRemoveSourceCodeEvent event) {
        try {
            writeLock.lock();
            dispatcherParamsService.unregisterSourceCode(event.sourceCodeUid);
        } finally {
            writeLock.unlock();
        }
    }

    @PostConstruct
    public void checkAndCreateNewDispatcher() {
        try {
            writeLock.lock();
            dispatcherParamsService.checkAndCreateNewDispatcher();
        } finally {
            writeLock.unlock();
        }
    }

    public void registerLongRunningExecContext(Long taskId, Long subExecContextId) {
        try {
            writeLock.lock();
            dispatcherParamsService.registerLongRunningExecContext(taskId, subExecContextId);
        } finally {
            writeLock.unlock();
        }
    }

    public void deRegisterLongRunningExecContext(Long taskId) {
        try {
            writeLock.lock();
            dispatcherParamsService.deRegisterLongRunningExecContext(taskId);
        } finally {
            writeLock.unlock();
        }
    }

    public void registerSourceCodes(List<SourceCodeImpl> sourceCodes) {
        try {
            writeLock.lock();
            dispatcherParamsService.registerSourceCodes(sourceCodes);
        } finally {
            writeLock.unlock();
        }
    }

    public void registerSourceCode(SourceCodeImpl sourceCode) {
        try {
            writeLock.lock();
            dispatcherParamsService.registerSourceCode(sourceCode);
        } finally {
            writeLock.unlock();
        }
    }

    public void unregisterSourceCode(String uid) {
        try {
            writeLock.lock();
            dispatcherParamsService.unregisterSourceCode(uid);
        } finally {
            writeLock.unlock();
        }
    }

    public boolean isLongRunning(Long taskId) {
        try {
            readLock.lock();
            return dispatcherParamsService.isLongRunning(taskId);
        } finally {
            readLock.unlock();
        }
    }

    public List<String> getExperiments() {
        try {
            readLock.lock();
            return dispatcherParamsService.getExperiments();
        } finally {
            readLock.unlock();
        }
    }

    public List<DispatcherParamsYaml.LongRunningExecContext> getLongRunningExecContexts() {
        try {
            readLock.lock();
            return dispatcherParamsService.getLongRunningExecContexts();
        } finally {
            readLock.unlock();
        }
    }

    public List<Long> getLongRunningTaskIds() {
        try {
            readLock.lock();
            return dispatcherParamsService.getLongRunningTaskIds();
        } finally {
            readLock.unlock();
        }
    }

    public List<String> getBatches() {
        try {
            readLock.lock();
            return dispatcherParamsService.getBatches();
        } finally {
            readLock.unlock();
        }
    }

}
