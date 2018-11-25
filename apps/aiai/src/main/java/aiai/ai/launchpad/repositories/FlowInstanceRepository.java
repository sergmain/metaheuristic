package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.FlowInstance;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FlowInstanceRepository extends CrudRepository<FlowInstance, Long> {

    @Override
    @Transactional(readOnly = true)
    List<FlowInstance> findAll();

    List<FlowInstance> findByExecStateOrderByCreatedOnAsc(int execSate);

    List<FlowInstance> findByExecState(int execState);

    Slice<FlowInstance> findByFlowId(Pageable pageable, long flowId);

    List<FlowInstance> findByFlowId(long flowId);
}

