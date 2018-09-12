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
import aiai.ai.beans.DatasetPath;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User: Serg
 * Date: 03.08.2017
 * Time: 21:00
 */
@Component
@Transactional
public interface DatasetPathRepository extends CrudRepository<DatasetPath, Long> {

    @Transactional(readOnly = true)
    List<DatasetPath> findByDataset(Dataset dataset);

    @Transactional(readOnly = true)
    List<DatasetPath> findByDataset_OrderByPathNumber(Dataset dataset);


    void deleteByDataset(Dataset dataset);
}