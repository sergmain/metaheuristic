package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.ExperimentTaskFeature;
import org.springframework.context.annotation.Profile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("launchpad")
public interface ExperimentTaskFeatureRepository extends CrudRepository<ExperimentTaskFeature, Long> {

    @Transactional
    void deleteByFlowInstanceId(long flowInstanceId);

    ExperimentTaskFeature findByTaskId(Long taskId);
}
