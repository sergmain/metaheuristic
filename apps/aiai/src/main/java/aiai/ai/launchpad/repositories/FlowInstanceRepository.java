package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface FlowInstanceRepository extends CrudRepository<FlowInstance, Long> {

    List<FlowInstance> findByCompletedFalseOrderByCreatedOnAsc();
}

