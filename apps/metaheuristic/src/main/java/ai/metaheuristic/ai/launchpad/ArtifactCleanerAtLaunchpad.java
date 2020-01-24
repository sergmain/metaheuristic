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

import ai.metaheuristic.ai.launchpad.repositories.BinaryDataRepository;
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

    private final WorkbookRepository workbookRepository;
    private final TaskRepository taskRepository;
    private final BinaryDataRepository binaryDataRepository;

    public void fixedDelay() {
        deleteOrphanTasks();
//# select count(*) from aiai.mh_data d where d.ref_type='batch' and d.REF_ID not in (select z.id from aiai.mh_batch z)
        deleteOrphanBatchData();
//# select count(*) from aiai.mh_data d where d.ref_type='workbook' and d.REF_ID not in (select z.id from aiai.mh_workbook z)
        deleteOrphanWorkbookData();

//select count(*) from aiai.mh_workbook w, aiai.mh_plan p
//where w.PLAN_ID=p.ID and p.PARAMS like '%archived: true%'

    }

    private void deleteOrphanWorkbookData() {
        deleteOrphanData(binaryDataRepository.findAllOrphanWorkbookData());
    }

    private void deleteOrphanBatchData() {
        deleteOrphanData(binaryDataRepository.findAllOrphanBatchData());
    }

    private void deleteOrphanData(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }

        // lets delete no more than 1000 record per call of ai.metaheuristic.ai.launchpad.ArtifactCleanerAtLaunchpad.deleteOrphanData()
        for (int i = 0; i < Math.min(ids.size(), 1000); i++) {
            binaryDataRepository.deleteById(ids.get(i));
        }
    }

    private void deleteOrphanTasks() {
        List<Long> ids = workbookRepository.findAllIds();;
        int page = 0;
        final AtomicBoolean isFound = new AtomicBoolean();
        do {
            isFound.set(false);
            taskRepository.findAllAsTaskSimple(PageRequest.of(page, 100))
                    .forEach(t -> {
                        isFound.set(true);
                        Long workbookId = (Long) t[1];
                        if (!ids.contains(workbookId)) {
                            log.info("Found orphan task #{}, workbookId: #{}", t[0], workbookId);
                            taskRepository.deleteById((Long) t[0]);
                        }
                    });
            page++;
        } while (isFound.get());
    }

    
}
