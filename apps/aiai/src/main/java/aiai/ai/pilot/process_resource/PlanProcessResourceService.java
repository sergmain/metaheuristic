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

package aiai.ai.pilot.process_resource;

import aiai.ai.pilot.beans.Batch;
import aiai.ai.pilot.beans.BatchWorkbook;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.launchpad.BinaryData;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.api.v1.launchpad.Task;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.File;
import java.util.List;

/**
 * @author Serge
 * Date: 5/9/2019
 * Time: 9:33 PM
 */
public interface PlanProcessResourceService {
    Page<Batch> batchRepositoryFindAllByOrderByCreatedOnDesc(Pageable pageable);

    Plan planCacheFindById(long id);

    List<BatchWorkbook> batchWorkbookRepositoryFindAllByBatchId(long batchId);

    Workbook workbookRepositoryFindById(Long workbookId);

    Iterable<Plan> planRepositoryFindAllAsPlan();

    PlanApiData.PlanValidation planServiceValidateInternal(Plan plan);

    Batch batchRepositorySave(Batch batch);

    void resourceServiceStoreInitialResource(File tempFile, String code, String poolCode, String originFilename);

    PlanApiData.TaskProducingResultComplex planServiceCreateWorkbook(long planId, String paramYaml);

    void batchWorkbookRepositorySave(BatchWorkbook batchWorkbook);

    PlanApiData.TaskProducingResultComplex planServiceProduceTasks(boolean isPersist, Plan plan, Workbook workbook);

    void planServiceChangeValidStatus(Workbook workbook, boolean status);

    OperationStatusRest planServiceWorkbookTargetExecState(Long workbookId, EnumsApi.WorkbookExecState execState);

    void planServiceCreateAllTasks();

    PlanApiData.WorkbookResult planServiceGetWorkbookExtended(Long workbookId);

    void planServiceDeleteWorkbook(Long workbookId, Long planId);

    Batch batchRepositoryFindById(Long batchId);

    Integer taskRepositoryFindMaxConcreteOrder(long workbookId);

    List<Task> taskRepositoryFindAnyWithConcreteOrder(long workbookId, int taskOrder);

    void binaryDataServiceStoreToFile(String code, File trgFile);

    List<BinaryData> binaryDataServiceGetByPoolCodeAndType(String poolCode, EnumsApi.BinaryDataType type);
}
