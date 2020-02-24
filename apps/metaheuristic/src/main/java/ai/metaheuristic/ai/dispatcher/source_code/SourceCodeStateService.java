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
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 2/23/2020
 * Time: 9:12 PM
 */
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class SourceCodeStateService {

    private final SourceCodeRepository sourceCodeRepository;
    private final SourceCodeCache sourceCodeCache;

    @Async
    @EventListener
    public void handleAsync(DispatcherInternalEvent.SourceCodeLockingEvent event) {
        setLockedTo(event.sourceCodeId, event.companyUniqueId, event.lock);
    }

    public void setValidTo(SourceCode sourceCode, boolean valid) {
        synchronized (syncObj) {
            SourceCodeImpl p = sourceCodeRepository.findByIdForUpdate(sourceCode.getId(), sourceCode.getCompanyId());
            if (p!=null && p.isValid()!=valid) {
                p.setValid(valid);
                saveInternal(p);
            }
            sourceCode.setValid(valid);
        }
    }

    private final static Object syncObj = new Object();

    private void setLockedTo(Long sourceCodeId, Long companyUniqueId, boolean locked) {
        synchronized (syncObj) {
            SourceCodeImpl p = sourceCodeRepository.findByIdForUpdate(sourceCodeId, companyUniqueId);
            if (p!=null && p.isLocked()!=locked) {
                p.setLocked(locked);
                saveInternal(p);
            }
        }
    }

    private void saveInternal(SourceCodeImpl sourceCode) {
        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        scspy.internalParams.updatedOn = System.currentTimeMillis();
        sourceCode.updateParams(scspy);

        sourceCodeCache.save(sourceCode);
    }

}
