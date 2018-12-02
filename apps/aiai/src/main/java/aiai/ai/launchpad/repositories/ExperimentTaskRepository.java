package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.ExperimentTask;
import aiai.ai.launchpad.beans.TaskExperimentFeature;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ExperimentTaskRepository extends CrudRepository<ExperimentTask, Long> {

    @Transactional
    void deleteByFlowInstanceId(long flowInstanceId);

}
