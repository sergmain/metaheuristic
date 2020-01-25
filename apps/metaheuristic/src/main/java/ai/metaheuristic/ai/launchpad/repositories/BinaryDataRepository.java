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

package ai.metaheuristic.ai.launchpad.repositories;

import ai.metaheuristic.ai.launchpad.beans.BinaryDataImpl;
import ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl;
import ai.metaheuristic.ai.launchpad.launchpad_resource.SimpleResource;
import ai.metaheuristic.api.launchpad.BinaryData;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Blob;
import java.util.Collection;
import java.util.List;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:41
 */
@Repository
@Profile("launchpad")
public interface BinaryDataRepository extends JpaRepository<BinaryDataImpl, Long> {

    @Transactional(readOnly = true)
    @Query(nativeQuery = true, value = "select d.id from mh_data d where d.WORKBOOK_ID is not null and d.WORKBOOK_ID not in (select z.id from mh_workbook z)")
    List<Long> findAllOrphanWorkbookData();

    @Transactional(readOnly = true)
    @Query(value="select b.id from BinaryDataImpl b where b.code=:code")
    Long getIdByCode(String code);

    @Query(value="select new ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl(" +
            "b.code, b.poolCode, b.params, b.filename ) " +
            "from BinaryDataImpl b where b.poolCode in :poolCodes and " +
            "b.workbookId=:workbookId")
    List<SimpleCodeAndStorageUrl> getCodeAndStorageUrlInPoolForWorkbook(List<String> poolCodes, Long workbookId);

    @Transactional(readOnly = true)
    @Query(value="select b.workbookId, b.filename from BinaryDataImpl b " +
            "where b.workbookId in :ids ")
    List<Object[]> getFilenamesForBatchIds(Collection<Long> ids);

    @Query(value="select new ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl(" +
            "b.code, b.poolCode, b.params, b.filename ) " +
            "from BinaryDataImpl b where b.poolCode in :poolCodes")
    List<SimpleCodeAndStorageUrl> getCodeAndStorageUrlInPoolForWorkbook(List<String> poolCodes);

    List<BinaryDataImpl> findAllByPoolCode(String poolCode);

    @Query(value="select b.filename from BinaryDataImpl b where b.poolCode=:poolCode and b.dataType=:dataType ")
    String findFilenameByPoolCodeAndDataType(String poolCode, int dataType);

    @Query(value="select b.filename from BinaryDataImpl b where and b.workbookId=:batchId ")
    String findFilenameByBatchId(Long batchId);

    @Transactional(readOnly = true)
    BinaryDataImpl findByCode(String code);

    @Transactional(readOnly = true)
    @Query(value="select b.data from BinaryDataImpl b where b.code=:code")
    Blob getDataAsStreamByCode(String code);

    @Transactional(readOnly = true)
    @Query(value="select b.data from BinaryDataImpl b where b.workbookId=:batchId ")
    Blob getDataAsStreamForBatchAndRefId(Long batchId);

    @Transactional
    @Query(value="select b from BinaryDataImpl b where b.code=:code")
    BinaryDataImpl findByCodeForUpdate(String code);

    @Transactional(readOnly = true)
    Page<BinaryDataImpl> findAll(Pageable pageable);

    @Transactional
    void deleteAllByDataType(int dataType);

    @Transactional
    void deleteByCodeAndDataType(String code, int dataType);

    @Transactional
    void deleteByWorkbookId(Long workbookId);

    @Transactional
    void deleteByPoolCodeAndDataType(String poolCode, int dataType);

    @Query(value="select new ai.metaheuristic.ai.launchpad.launchpad_resource.SimpleResource(" +
            "b.id, b.version, b.code, b.poolCode, b.dataType, b.uploadTs, b.filename, b.params ) " +
            "from BinaryDataImpl b " +
            "order by b.uploadTs desc ")
    Slice<SimpleResource> getAllAsSimpleResources(Pageable pageable);


    @Query(value="select b.code from BinaryDataImpl b")
    List<String> getAllCodes();
}
