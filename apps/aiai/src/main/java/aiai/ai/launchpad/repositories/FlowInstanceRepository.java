package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface FlowInstanceRepository extends CrudRepository<FlowInstance, Long> {

    List<FlowInstance> findByCompletedFalseOrderByCreatedOnAsc();

    List<FlowInstance> findByCompletedFalse();

    Slice<FlowInstance> findByFlowId(Pageable pageable, long flowId);
}

