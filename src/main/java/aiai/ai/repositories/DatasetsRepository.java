/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

package aiai.ai.repositories;

import aiai.ai.beans.Dataset;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * User: Serg
 * Date: 15.06.2017
 * Time: 19:53
 */
@Component
@Transactional
public interface DatasetsRepository extends CrudRepository<Dataset, Long> {

    @Transactional(readOnly = true)
    Slice<Dataset> findAll(Pageable pageable);

/*

    @Transactional(rollbackFor = {Throwable.class})
    List<Dataset> findByLastname(String lastname, Sort sort);

    List<Dataset> findByLastname(String lastname, Pageable pageable);

    Long deleteByLastname(String lastname);

    List<Dataset> removeByLastname(String lastname);

*/
}