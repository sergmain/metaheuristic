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

import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.launchpad_resource.ResourceService;
import aiai.ai.launchpad.plan.PlanCache;
import aiai.ai.launchpad.plan.PlanService;
import aiai.ai.launchpad.repositories.PlanRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.repositories.WorkbookRepository;
import aiai.ai.pilot.beans.Batch;
import aiai.ai.pilot.beans.BatchWorkbook;
import metaheuristic.api.v1.EnumsApi;
import metaheuristic.api.v1.data.OperationStatusRest;
import metaheuristic.api.v1.data.PlanApiData;
import metaheuristic.api.v1.launchpad.BinaryData;
import metaheuristic.api.v1.launchpad.Plan;
import metaheuristic.api.v1.launchpad.Task;
import metaheuristic.api.v1.launchpad.Workbook;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * @author Serge
 * Date: 5/9/2019
 * Time: 9:33 PM
 */
@Component
@Profile("launchpad")
public class PlanProcessResourceServiceImpl implements PlanProcessResourceService {

    private final WorkbookRepository workbookRepository;
    private final PlanRepository planRepository;
    private final PlanCache planCache;
    private final PlanService planService;
    private final ResourceService resourceService;
    private final TaskRepository taskRepository;
    private final BatchRepository batchRepository;
    private final BatchWorkbookRepository batchWorkbookRepository;
    private final BinaryDataService binaryDataService;

    public PlanProcessResourceServiceImpl(WorkbookRepository workbookRepository, PlanRepository planRepository, PlanCache planCache, PlanService planService, ResourceService resourceService, TaskRepository taskRepository, BatchRepository batchRepository, BatchWorkbookRepository batchWorkbookRepository, BinaryDataService binaryDataService) {
        this.workbookRepository = workbookRepository;
        this.planRepository = planRepository;
        this.planCache = planCache;
        this.planService = planService;
        this.resourceService = resourceService;
        this.taskRepository = taskRepository;
        this.batchRepository = batchRepository;
        this.batchWorkbookRepository = batchWorkbookRepository;
        this.binaryDataService = binaryDataService;
    }

    @Override
    public Page<Batch> batchRepositoryFindAllByOrderByCreatedOnDesc(Pageable pageable) {
        return batchRepository.findAllByOrderByCreatedOnDesc(pageable);
    }

    @Override
    public Plan planCacheFindById(long id) {
        return planCache.findById(id);
    }

    @Override
    public List<BatchWorkbook> batchWorkbookRepositoryFindAllByBatchId(long batchId) {
        return batchWorkbookRepository.findAllByBatchId(batchId);
    }

    @Override
    public Workbook workbookRepositoryFindById(Long workbookId) {
        return workbookRepository.findById(workbookId).orElse(null);
    }

    @Override
    public Iterable<Plan> planRepositoryFindAllAsPlan() {
        return planRepository.findAllAsPlan();
    }

    @Override
    public PlanApiData.PlanValidation planServiceValidateInternal(Plan plan) {
        return planService.validateInternal(plan);
    }

    @Override
    public Batch batchRepositorySave(Batch batch) {
        return batchRepository.save(batch);
    }

    @Override
    public void resourceServiceStoreInitialResource(File tempFile, String code, String poolCode, String originFilename) {
        resourceService.storeInitialResource(tempFile, code, poolCode, originFilename);
    }

    @Override
    public PlanApiData.TaskProducingResultComplex planServiceCreateWorkbook(long planId, String paramYaml) {
        return planService.createWorkbook(planId, paramYaml);
    }

    @Override
    public void batchWorkbookRepositorySave(BatchWorkbook batchWorkbook) {
        batchWorkbookRepository.save(batchWorkbook);
    }

    @Override
    public PlanApiData.TaskProducingResultComplex planServiceProduceTasks(boolean isPersist, Plan plan, Workbook workbook) {
        return planService.produceTasks(isPersist, plan, workbook);
    }

    @Override
    public void planServiceChangeValidStatus(Workbook workbook, boolean status) {
        planService.changeValidStatus(workbook, status);
    }

    @Override
    public OperationStatusRest planServiceWorkbookTargetExecState(Long workbookId, EnumsApi.WorkbookExecState execState) {
        return planService.workbookTargetExecState(workbookId, execState);
    }

    @Override
    public void planServiceCreateAllTasks() {
        planService.createAllTasks();
    }

    @Override
    public PlanApiData.WorkbookResult planServiceGetWorkbookExtended(Long workbookId) {
        return planService.getWorkbookExtended(workbookId);
    }

    @Override
    public void planServiceDeleteWorkbook(Long workbookId, Long planId) {
        planService.deleteWorkbook(workbookId, planId);
    }

    @Override
    public Batch batchRepositoryFindById(Long batchId) {
        return batchRepository.findById(batchId).orElse(null);
    }

    @Override
    public Integer taskRepositoryFindMaxConcreteOrder(long workbookId) {
        return taskRepository.findMaxConcreteOrder(workbookId);
    }

    @Override
    public List<Task> taskRepositoryFindAnyWithConcreteOrder(long workbookId, int taskOrder) {
        return taskRepository.findAnyWithConcreteOrder(workbookId, taskOrder);
    }

    @Override
    public void binaryDataServiceStoreToFile(String code, File trgFile) {
        binaryDataService.storeToFile(code, trgFile);
    }

    @Override
    public List<BinaryData> binaryDataServiceGetByPoolCodeAndType(String poolCode, EnumsApi.BinaryDataType type) {
        return binaryDataService.getByPoolCodeAndType(poolCode, type);
    }

}
