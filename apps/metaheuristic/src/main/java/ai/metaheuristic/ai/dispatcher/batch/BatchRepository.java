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

package ai.metaheuristic.ai.mh.dispatcher..batch;

import ai.metaheuristic.ai.mh.dispatcher..batch.data.BatchAndExecContextStates;
import ai.metaheuristic.ai.mh.dispatcher..batch.data.BatchExecStatus;
import ai.metaheuristic.ai.mh.dispatcher..beans.Batch;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("mh.dispatcher.")
@Transactional(propagation = Propagation.REQUIRES_NEW)
public interface BatchRepository extends JpaRepository<Batch, Long> {

    @Query(value="select b from Batch b where b.id=:id and b.companyId=:companyUniqueId")
    Batch findByIdForUpdate(Long id, Long companyUniqueId);

    @Query(value="select b from Batch b where b.id=:id")
    Batch findByIdForUpdate(Long id);

    @Transactional(readOnly = true)
    @Query("select b.id from Batch b where b.companyId=:companyUniqueId order by b.createdOn desc")
    Page<Long> findAllByOrderByCreatedOnDesc(Pageable pageable, Long companyUniqueId);

    @Transactional(readOnly = true)
    @Query("select b.id from Batch b where b.companyId=:companyUniqueId and b.accountId=:accountId order by b.createdOn desc")
    Page<Long> findAllForAccountByOrderByCreatedOnDesc(Pageable pageable, Long companyUniqueId, Long accountId);

    @Transactional(readOnly = true)
    @Query("select b.id from Batch b where b.companyId=:companyUniqueId and b.deleted=false order by b.createdOn desc")
    Page<Long> findAllExcludeDeletedByOrderByCreatedOnDesc(Pageable pageable, Long companyUniqueId);

    @Transactional(readOnly = true)
    @Query("select b.id from Batch b where b.companyId=:companyUniqueId and b.deleted=false and b.accountId=:accountId order by b.createdOn desc")
    Page<Long> findAllForAccountExcludeDeletedByOrderByCreatedOnDesc(Pageable pageable, Long companyUniqueId, Long accountId);

    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.mh.dispatcher..batch.data.BatchExecStatus(b.id, b.execState) " +
            "from Batch b where b.companyId=:companyUniqueId")
    List<BatchExecStatus> getBatchExecStatuses(Long companyUniqueId);

/*

    public enum BatchExecState {
        Error(-1, "Error"),
        Unknown(0, "None"),
        Stored(1, "Stored"),
        Preparing(2, "Preparing"),
        Processing(3, "Processing"),
        Finished(4, "Finished"),
        Archived(5, "Archived") ;

    public enum ExecContextState {
        ERROR(-2),          // some error in configuration
        UNKNOWN(-1),        // unknown state
        NONE(0),            // just created execContext
        PRODUCING(1),       // producing was just started
        PRODUCED(2),        // producing was finished
        STARTED(3),         // started
        STOPPED(4),         // stopped
        FINISHED(5),        // finished
        DOESNT_EXIST(6),    // doesn't exist. this state is needed at station side to reconcile list of experiments
        EXPORTING_TO_ATLAS(7),    // execContext is marked as needed to be exported to atlas
        EXPORTING_TO_ATLAS_WAS_STARTED(8),    // execContext is marked as needed to be exported to atlas and export was started
        EXPORTED_TO_ATLAS(9);    // execContext was exported to atlas

    if (batch!=null && batch.execState != Enums.BatchExecState.Finished.code &&
    batch.execState != Enums.BatchExecState.Error.code &&
    batch.execState != Enums.BatchExecState.Archived.code) {
        boolean isFinished = false;
        for (Integer execState : execContextRepository.findExecContextStateByBatchId(batch.id)) {
            isFinished = true;
            if (execState != EnumsApi.ExecContextState.ERROR.code && execState != EnumsApi.ExecContextState.FINISHED.code) {
                break;
            }
*/
    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.mh.dispatcher..batch.data.BatchAndExecContextStates(b.id, w.id, b.execState, w.state) " +
            "from Batch b, ExecContextImpl w " +
            "where b.execContextId=w.id and b.execState=3")
    List<BatchAndExecContextStates> findAllUnfinished();

}
