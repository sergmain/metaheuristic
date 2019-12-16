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

package ai.metaheuristic.ai.launchpad.batch;

import ai.metaheuristic.ai.launchpad.batch.data.BatchAndWorkbookExecStates;
import ai.metaheuristic.ai.launchpad.batch.data.BatchExecStatus;
import ai.metaheuristic.ai.launchpad.beans.Batch;
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
@Profile("launchpad")
@Transactional(propagation = Propagation.REQUIRES_NEW)
public interface BatchRepository extends JpaRepository<Batch, Long> {

    @Query(value="select b from Batch b where b.id=:id and b.companyId=:companyId")
    Batch findByIdForUpdate(Long id, Long companyId);

    @Query(value="select b from Batch b where b.id=:id")
    Batch findByIdForUpdate(Long id);

    @Transactional(readOnly = true)
    @Query("select b.id from Batch b where b.companyId=:companyId order by b.createdOn desc")
    Page<Long> findAllByOrderByCreatedOnDesc(Pageable pageable, Long companyId);

    @Transactional(readOnly = true)
    @Query("select b.id from Batch b where b.companyId=:companyId and b.deleted=false order by b.createdOn desc")
    Page<Long> findAllExcludeDeletedByOrderByCreatedOnDesc(Pageable pageable, Long companyId);

    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.launchpad.batch.data.BatchExecStatus(b.id, b.execState) " +
            "from Batch b where b.companyId=:companyId")
    List<BatchExecStatus> getBatchExecStatuses(Long companyId);

/*

    public enum BatchExecState {
        Error(-1, "Error"),
        Unknown(0, "None"),
        Stored(1, "Stored"),
        Preparing(2, "Preparing"),
        Processing(3, "Processing"),
        Finished(4, "Finished"),
        Archived(5, "Archived") ;

    public enum WorkbookExecState {
        ERROR(-2),          // some error in configuration
        UNKNOWN(-1),        // unknown state
        NONE(0),            // just created workbook
        PRODUCING(1),       // producing was just started
        PRODUCED(2),        // producing was finished
        STARTED(3),         // started
        STOPPED(4),         // stopped
        FINISHED(5),        // finished
        DOESNT_EXIST(6),    // doesn't exist. this state is needed at station side to reconcile list of experiments
        EXPORTING_TO_ATLAS(7),    // workbook is marked as needed to be exported to atlas
        EXPORTING_TO_ATLAS_WAS_STARTED(8),    // workbook is marked as needed to be exported to atlas and export was started
        EXPORTED_TO_ATLAS(9);    // workbook was exported to atlas

    if (batch!=null && batch.execState != Enums.BatchExecState.Finished.code &&
    batch.execState != Enums.BatchExecState.Error.code &&
    batch.execState != Enums.BatchExecState.Archived.code) {
        boolean isFinished = false;
        for (Integer execState : workbookRepository.findWorkbookExecStateByBatchId(batch.id)) {
            isFinished = true;
            if (execState != EnumsApi.WorkbookExecState.ERROR.code && execState != EnumsApi.WorkbookExecState.FINISHED.code) {
                break;
            }
*/
    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.launchpad.batch.data.BatchAndWorkbookExecStates(b.id, w.id, b.execState, w.execState) " +
            "from Batch b, BatchWorkbook bw, WorkbookImpl w " +
            "where b.id=bw.batchId and bw.workbookId=w.id and b.execState=3")
    List<BatchAndWorkbookExecStates> findAllUnfinished();

}
