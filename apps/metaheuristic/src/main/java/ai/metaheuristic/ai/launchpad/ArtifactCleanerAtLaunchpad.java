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
package ai.metaheuristic.ai.launchpad;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class ArtifactCleanerAtLaunchpad {

    private final Globals globals;
    private final WorkbookRepository workbookRepository;
    private final TaskRepository taskRepository;

    public int cleanTasks(List<Long> ids, int page, AtomicBoolean isFound) {
        taskRepository.findAllAsTaskSimple(PageRequest.of(page++, 100))
                .forEach(t -> {
                    isFound.set(true);
                    Long workbookIdd = (Long) t[1];
                    if (!ids.contains(workbookIdd)) {
                        log.info("Found orphan task #{}, workbookId: #{}", t[0], workbookIdd);
                        taskRepository.deleteById((Long) t[0]);
                    }
                });
        return page;
    }

    public void fixedDelay() {
        // TODO 2019-08-26 maybe we have to delete this because we already have @Profile("launchpad")
        if (!globals.isLaunchpadEnabled) {
            return;
        }

        List<Long> ids = workbookRepository.findAllIds();;

        int page = 0;
        final AtomicBoolean isFound = new AtomicBoolean();
        do {
            isFound.set(false);
            page = cleanTasks(ids, page, isFound);
        } while (isFound.get());
    }
}
