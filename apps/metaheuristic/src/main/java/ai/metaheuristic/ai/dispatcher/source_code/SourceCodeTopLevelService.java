/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class SourceCodeTopLevelService {

    private final SourceCodeStateService sourceCodeStateService;

    private final static Object syncObj = new Object();

    @Async
    @EventListener
    public void setLockedTo(DispatcherInternalEvent.SourceCodeLockingEvent event) {
        setLockedTo(event.sourceCodeId, event.companyUniqueId, event.lock);
    }

    private void setLockedTo(Long sourceCodeId, @Nullable Long companyUniqueId, boolean locked) {
        synchronized (syncObj) {
            sourceCodeStateService.setLockedTo(sourceCodeId, companyUniqueId, locked);
        }
    }

    public void setValidTo(SourceCodeImpl sourceCode, boolean valid) {
        synchronized (syncObj) {
            sourceCodeStateService.setValidTo(sourceCode, sourceCode.companyId, valid);
        }
    }

}
