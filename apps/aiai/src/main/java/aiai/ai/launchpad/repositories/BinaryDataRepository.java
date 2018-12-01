/*
 * AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov
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

import aiai.ai.launchpad.beans.BinaryData;
import aiai.ai.launchpad.resource.SimpleResource;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:41
 */
@Repository
@Profile("launchpad")
public interface BinaryDataRepository extends CrudRepository<BinaryData, Long> {
    List<BinaryData> findAllByDataType(int dataType);

    List<BinaryData> findAllByPoolCode(String poolCode);

    BinaryData findByCode(String code);

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    Slice<BinaryData> findAll(Pageable pageable);

    @Transactional
    void deleteAllByDataType(int dataType);

    @Transactional
    void deleteByCodeAndDataType(String code, int dataType);

    @Transactional
    void deleteByFlowInstanceId(long flowInstanceId);

    @Transactional
    void deleteByPoolCodeAndDataType(String poolCode, int dataType);

    @Query(value="select new aiai.ai.launchpad.resource.SimpleResource(" +
            "b.id, b.version, b.code, b.poolCode, b.dataType, b.uploadTs, b.checksum, b.valid, b.manual, b.filename ) " +
            "from BinaryData b")
    Slice<SimpleResource> getAllAsSimpleResources(Pageable pageable);

}
