package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import org.springframework.data.repository.CrudRepository;

public interface FlowInstanceRepository extends CrudRepository<FlowInstance, Long> {
}

