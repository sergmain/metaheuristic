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

package ai.metaheuristic.ai.dispatcher.repositories;

import ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultSimple;
import ai.metaheuristic.ai.dispatcher.beans.ExperimentResult;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("dispatcher")
public interface ExperimentResultRepository extends CrudRepository<ExperimentResult, Long> {

    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.experiment_result.AtlasSimple(" +
            "b.id, b.name, b.description, b.createdOn ) from ExperimentResult b order by b.id desc")
    Slice<ExperimentResultSimple> findAllAsSimple(Pageable pageable);

    @Nullable
    @Transactional(readOnly = true)
    @Query(value="select a.id from ExperimentResult a where a.id=:atlasId")
    Long findIdById(Long atlasId);
}
