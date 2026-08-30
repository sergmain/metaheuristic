/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.meta_storage_rdbms.repositories;

import ai.metaheuristic.meta_storage_rdbms.beans.MetaStorageRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repository behind the JPA implementation of {@code MetaStorageSpi}.
 *
 * <p>Every query is JPQL, so the same four operations run unchanged on H2, MySQL, MariaDB and
 * PostgreSQL - Hibernate emits the dialect. Compare the SQLite implementation, where the upsert is a
 * literal {@code ON CONFLICT ... DO UPDATE} that only two of those engines would accept.
 *
 * @author Serge
 */
@Repository
@Transactional
public interface MetaStorageRecordRepository extends JpaRepository<MetaStorageRecord, Long> {

    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT s FROM MetaStorageRecord s WHERE s.bucket=:bucket AND s.type=:type AND s.recKey=:recKey")
    MetaStorageRecord findByNaturalKey(@Param("bucket") String bucket, @Param("type") String type,
                                     @Param("recKey") String recKey);

    /** Every record of one type, ordered by recKey so a run is reproducible. */
    @Transactional(readOnly = true)
    @Query("SELECT s FROM MetaStorageRecord s WHERE s.bucket=:bucket AND s.type=:type ORDER BY s.recKey")
    List<MetaStorageRecord> findAllByBucketAndType(@Param("bucket") String bucket, @Param("type") String type);

    /** A named subset - the per-batch payload fetch, once the splitter has handed out its slice. */
    @Transactional(readOnly = true)
    @Query("SELECT s FROM MetaStorageRecord s WHERE s.bucket=:bucket AND s.type=:type AND s.recKey IN :recKeys ORDER BY s.recKey")
    List<MetaStorageRecord> findAllByBucketAndTypeAndRecKeys(@Param("bucket") String bucket, @Param("type") String type,
                                                           @Param("recKeys") List<String> recKeys);

    /** Key list only - the selection step that feeds the batch splitter. Never materialises bodies. */
    @Transactional(readOnly = true)
    @Query("SELECT s.recKey FROM MetaStorageRecord s WHERE s.bucket=:bucket AND s.type=:type ORDER BY s.recKey")
    List<String> findRecKeysByBucketAndType(@Param("bucket") String bucket, @Param("type") String type);

    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT max(s.gen) FROM MetaStorageRecord s WHERE s.bucket=:bucket AND s.type=:type")
    Long findMaxGen(@Param("bucket") String bucket, @Param("type") String type);

    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT max(s.gen) FROM MetaStorageRecord s")
    Long findMaxGenGlobally();
}
