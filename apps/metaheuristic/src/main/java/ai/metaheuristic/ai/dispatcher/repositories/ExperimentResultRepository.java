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

package ai.metaheuristic.ai.dispatcher.repositories;

import ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultSimple;
import ai.metaheuristic.ai.dispatcher.beans.ExperimentResult;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("dispatcher")
@Transactional
public interface ExperimentResultRepository extends CrudRepository<ExperimentResult, Long> {

    @Override
    @Modifying
    @Query(value="delete from ExperimentResult t where t.id=:id")
    void deleteById(Long id);

    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultSimple(" +
            "b.id, b.name, b.code, b.description, b.createdOn ) from ExperimentResult b order by b.id desc")
    Slice<ExperimentResultSimple> findAllAsSimple(Pageable pageable);

    @Transactional(readOnly = true)
    @Nullable
    @Query(value="select a.id from ExperimentResult a where a.id=:experimentResultId")
    Long findIdById(Long experimentResultId);

    @Query(value="select b.id, b.name from ExperimentResult b order by b.id desc")
    List<Object[]> getResultNames();
}
