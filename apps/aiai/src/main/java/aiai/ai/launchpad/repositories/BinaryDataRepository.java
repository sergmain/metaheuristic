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

import aiai.ai.launchpad.beans.BinaryDataImpl;
import metaheuristic.api.v1.launchpad.BinaryData;
import aiai.ai.launchpad.binary_data.SimpleCodeAndStorageUrl;
import aiai.ai.launchpad.launchpad_resource.SimpleResource;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:41
 */
@Repository
@Profile("launchpad")
public interface BinaryDataRepository extends CrudRepository<BinaryDataImpl, Long> {
    List<BinaryData> findAllByDataType(int dataType);

    @Query(value="select new aiai.ai.launchpad.binary_data.SimpleCodeAndStorageUrl(" +
            "b.code, b.poolCode, b.params ) " +
            "from BinaryDataImpl b where b.poolCode in :poolCodes and b.workbookId=:workbookId")
    List<SimpleCodeAndStorageUrl> getCodeAndStorageUrlInPool(List<String> poolCodes, long workbookId);

    @Query(value="select new aiai.ai.launchpad.binary_data.SimpleCodeAndStorageUrl(" +
            "b.code, b.poolCode, b.params ) " +
            "from BinaryDataImpl b where b.poolCode in :poolCodes")
    List<SimpleCodeAndStorageUrl> getCodeAndStorageUrlInPool(List<String> poolCodes);

    @Query(value="select new aiai.ai.launchpad.binary_data.SimpleCodeAndStorageUrl(" +
            "b.code, b.poolCode, b.params ) " +
            "from BinaryDataImpl b where b.code in :codes ")
    List<SimpleCodeAndStorageUrl> getCodeAndStorageUrl(List<String> codes);

    List<BinaryDataImpl> findAllByPoolCode(String poolCode);

    List<BinaryData> findAllByPoolCodeAndDataType(String poolCode, int dataType);

    BinaryDataImpl findByCode(String code);

    @Transactional(readOnly = true)
    Slice<BinaryData> findAll(Pageable pageable);

    @Transactional
    void deleteAllByDataType(int dataType);

    @Transactional
    void deleteByCodeAndDataType(String code, int dataType);

    @Transactional
    void deleteByWorkbookId(long workbookId);

    @Transactional
    void deleteByPoolCodeAndDataType(String poolCode, int dataType);

    @Query(value="select new aiai.ai.launchpad.launchpad_resource.SimpleResource(" +
            "b.id, b.version, b.code, b.poolCode, b.dataType, b.uploadTs, b.checksum, b.valid, b.manual, b.filename, " +
            "b.params ) " +
            "from BinaryDataImpl b where b.manual=true ")
    Slice<SimpleResource> getAllAsSimpleResources(Pageable pageable);

}
