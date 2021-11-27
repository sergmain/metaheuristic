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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.TaskWithInternalContextEventService;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingTopLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * @author Serge
 * Date: 10/30/2020
 * Time: 7:14 PM
 */
@SuppressWarnings({"unused", "MethodMayBeStatic"})
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class EventBusService {

    public final TaskCheckCachingTopLevelService taskCheckCachingTopLevelService;
    public final TaskWithInternalContextEventService taskWithInternalContextEventService;
    public final TaskFinishingTopLevelService taskFinishingTopLevelService;
    public final DispatcherParamsTopLevelService dispatcherParamsTopLevelService;
    public final ExecContextTopLevelService execContextTopLevelService;
    public final ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;

    @Async
    @EventListener
    public void registerVariableState(VariableUploadedEvent event) {
        try {
            log.debug("call EventBusService.registerVariableStates(execContextId:#{}, taskId:#{}, variableId:#{}, nullified:{})", event.execContextId, event.taskId, event.variableId, event.nullified);
            ExecContextVariableStateTopLevelService.registerVariableState(event);
        } catch (Throwable th) {
            log.error("Error, need to investigate ", th);
        }
    }

    @Async
    @EventListener
    public void registerCreatedTask(TaskCreatedEvent event) {
        try {
            ExecContextVariableStateTopLevelService.registerCreatedTask(event);
        } catch (Throwable th) {
            log.error("Error, need to investigate ", th);
        }
    }

    @Async
    @EventListener
    public void checkTaskCanBeFinished(CheckTaskCanBeFinishedEvent event) {
        try {
            log.debug("call EventBusService.checkTaskCanBeFinished(execContextId:#{}, taskId:#{})", event.execContextId, event.taskId);
            taskFinishingTopLevelService.checkTaskCanBeFinished(event.taskId);
        } catch (Throwable th) {
            log.error("Error, need to investigate ", th);
        }
    }

    @Async
    @EventListener
    public void processInternalFunction(final TaskWithInternalContextEvent event) {
        try {
            taskWithInternalContextEventService.putToQueue(event);
        } catch (Throwable th) {
            log.error("Error, need to investigate ", th);
        }
    }

    @SuppressWarnings("unused")
    @Async
    @EventListener
    public void checkAndCreateNewDispatcher(final DispatcherCacheCheckingEvent event) {
        try {
            dispatcherParamsTopLevelService.checkAndCreateNewDispatcher();
        } catch (Throwable th) {
            log.error("Error, need to investigate ", th);
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Async
    @EventListener
    public void resourceCloseEvent(ResourceCloseEvent event) {
        // !!! DO NOT CHANGE THE ORDER OF CLOSING: InputStream - FIRST, File - SECOND, Dirs - THIRDS
        for (InputStream inputStream : event.inputStreams) {
            try {
                inputStream.close();
            }
            catch(Throwable th)  {
                log.warn("#448.020 Error while closing stream", th);
            }
        }
        for (File file : event.files) {
            if (file.isDirectory()) {
                log.error("#448.030 error in code. path {} is a directory", file.getAbsolutePath());
                continue;
            }
            try {
                Files.delete(file.toPath());
            }
            catch(Throwable th)  {
                log.warn("#448.040 Error while deleting file "+ file.getAbsolutePath(), th);
            }
        }
        for (File dir : event.dirs) {
            if (dir.isFile()) {
                log.error("#448.060 error in code. path {} is a file", dir.getAbsolutePath());
                continue;
            }
            try {
                FileUtils.deleteDirectory(dir);
            }
            catch(Throwable th)  {
                log.warn("#448.080 Error while deleting dir "+ dir.getAbsolutePath(), th);
            }
        }
    }
}
