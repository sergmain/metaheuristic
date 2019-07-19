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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 5/29/2019
 * Time: 7:25 PM
 */

@Service
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class WorkbookCache {

    private final WorkbookRepository workbookRepository;

    @CacheEvict(cacheNames = {Consts.WORKBOOK_CACHE}, allEntries = true)
    public void clearCache() {
    }

    @CacheEvict(cacheNames = {Consts.WORKBOOK_CACHE}, key = "#result.id")
    public WorkbookImpl save(WorkbookImpl workbook) {
        if (workbook==null) {
            return null;
        }
        log.info("#461.010 save workbook, id: #{}, workbook: {}", workbook.id, workbook);
        return workbookRepository.save(workbook);
    }

    @CacheEvict(cacheNames = {Consts.WORKBOOK_CACHE}, key = "#workbook.id")
    public void delete(WorkbookImpl workbook) {
        if (workbook==null || workbook.id==null) {
            return;
        }
        try {
            workbookRepository.delete(workbook);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#461.030 Error deleting of workbook by object", e);
        }
    }

    @CacheEvict(cacheNames = {Consts.WORKBOOK_CACHE}, key = "#id")
    public void evictById(Long id) {
        //
    }

    @CacheEvict(cacheNames = {Consts.WORKBOOK_CACHE}, key = "#workbookId")
    public void delete(Long workbookId) {
        if (workbookId==null) {
            return;
        }
        try {
            workbookRepository.deleteById(workbookId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#461.050 Error deleting of workbook by id", e);
        }
    }

    @CacheEvict(cacheNames = {Consts.WORKBOOK_CACHE}, key = "#workbookId")
    public void deleteById(Long workbookId) {
        if (workbookId==null) {
            return;
        }
        try {
            workbookRepository.deleteById(workbookId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#461.070 Error deleting of workbook by id", e);
        }
    }

    @Cacheable(cacheNames = {Consts.WORKBOOK_CACHE}, unless="#result==null")
    public WorkbookImpl findById(Long id) {
        return workbookRepository.findById(id).orElse(null);
    }
}
