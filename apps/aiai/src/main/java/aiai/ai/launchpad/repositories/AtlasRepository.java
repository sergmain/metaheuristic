/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.atlas.AtlasSimple;
import aiai.ai.launchpad.beans.Atlas;
import aiai.ai.launchpad.beans.Experiment;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@Profile("launchpad")
public interface AtlasRepository extends JpaRepository<Atlas, Long> {

    @Transactional(readOnly = true)
    @Query(value="select new aiai.ai.launchpad.atlas.AtlasSimple(" +
            "b.id, b.experiment, b.name, b.description ) from Atlas b order by b.id desc")
    Slice<AtlasSimple> findAllAsSimple(Pageable pageable);

    @Transactional(readOnly = true)
    Slice<Atlas> findAllByOrderByIdDesc(Pageable pageable);

}
