/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.workbook;

import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 8/11/2019
 * Time: 10:56 AM
 */
@SuppressWarnings("Duplicates")
public class WorkbookFunctions {

    private static final ConcurrentHashMap<Long, AtomicInteger> syncMap = new ConcurrentHashMap<>(100, 0.75f, 10);

    static Long requestAndReturnLong(Long workbookId, Supplier<Long> function) {
        final AtomicInteger obj = syncMap.computeIfAbsent(workbookId, o -> new AtomicInteger());
        obj.incrementAndGet();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return function.get();
            }
            finally {
                if (obj.get()==1) {
                    syncMap.remove(workbookId);
                }
                obj.decrementAndGet();
            }
        }
    }

    static OperationStatusRest requestAndReturnOperationStatusRest(Long workbookId, Supplier<OperationStatusRest> function) {
        final AtomicInteger obj = syncMap.computeIfAbsent(workbookId, o -> new AtomicInteger());
        obj.incrementAndGet();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return function.get();
            }
            finally {
                if (obj.get()==1) {
                    syncMap.remove(workbookId);
                }
                obj.decrementAndGet();
            }
        }
    }

    static List<WorkbookParamsYaml.TaskVertex> requestAndReturnListVertices(Long workbookId, Supplier<List<WorkbookParamsYaml.TaskVertex>> function) {
        final AtomicInteger obj = syncMap.computeIfAbsent(workbookId, o -> new AtomicInteger());
        obj.incrementAndGet();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return function.get();
            }
            finally {
                if (obj.get()==1) {
                    syncMap.remove(workbookId);
                }
                obj.decrementAndGet();
            }
        }
    }

    static Set<WorkbookParamsYaml.TaskVertex> requestAndReturnSetVertices(Long workbookId, Supplier<Set<WorkbookParamsYaml.TaskVertex>> function) {
        final AtomicInteger obj = syncMap.computeIfAbsent(workbookId, o -> new AtomicInteger());
        obj.incrementAndGet();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return function.get();
            }
            finally {
                if (obj.get()==1) {
                    syncMap.remove(workbookId);
                }
                obj.decrementAndGet();
            }
        }
    }

    static WorkbookOperationStatusWithTaskList requestAndReturnTaskList(Long workbookId, Supplier<WorkbookOperationStatusWithTaskList> function) {
        final AtomicInteger obj = syncMap.computeIfAbsent(workbookId, o -> new AtomicInteger());
        obj.incrementAndGet();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return function.get();
            }
            finally {
                if (obj.get()==1) {
                    syncMap.remove(workbookId);
                }
                obj.decrementAndGet();
            }
        }
    }
}
