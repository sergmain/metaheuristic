/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.repositories;

import ai.metaheuristic.ai.dispatcher.beans.ExperimentTask;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
@Transactional
@Profile("dispatcher")
public interface ExperimentTaskRepository extends CrudRepository<ExperimentTask, Long> {

    @Override
    @Modifying
    @Query(value="delete from ExperimentTask t where t.id=:id")
    void deleteById(Long id);

    @Transactional(readOnly = true)
    @Query(value="select t.id from ExperimentTask t where t.experimentResultId=:experimentResultId ")
    List<Long> findAllAsTaskSimple(Pageable pageable, Long experimentResultId);

    @Transactional(readOnly = true)
    @Query("SELECT at FROM ExperimentTask at where at.experimentResultId=:experimentResultId and at.taskId in :ids ")
    List<ExperimentTask> findTasksById(Long experimentResultId, Collection<Long> ids);

    @Transactional(readOnly = true)
    @Query("SELECT at.id FROM ExperimentTask at where at.experimentResultId=:experimentResultId ")
    Set<Long> findIdsByExperimentResultId(Long experimentResultId);

    @Transactional(readOnly = true)
    @Nullable
    ExperimentTask findByExperimentResultIdAndTaskId(Long experimentResultId, Long taskId);

    void deleteByExperimentResultId(Long experimentResultId);
}
