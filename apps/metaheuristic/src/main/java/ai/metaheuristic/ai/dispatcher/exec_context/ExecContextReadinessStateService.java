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

package ai.metaheuristic.ai.dispatcher.exec_context;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Serge
 * Date: 9/27/2021
 * Time: 12:22 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextReadinessStateService {
    private final LinkedList<Long> notReadyExecContextIds = new LinkedList<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public boolean addAll(List<Long> execContextIds) {
        writeLock.lock();
        try {
            return notReadyExecContextIds.addAll(execContextIds);
        } finally {
            writeLock.unlock();
        }
    }

    public boolean isNotReady(Long execContextId) {
        readLock.lock();
        try {
            return !notReadyExecContextIds.isEmpty() && notReadyExecContextIds.contains(execContextId);
        } finally {
            readLock.unlock();
        }
    }

    public boolean remove(Long execContextId) {
        writeLock.lock();
        try {
            return notReadyExecContextIds.remove(execContextId);
        } finally {
            writeLock.unlock();
        }
    }
}
