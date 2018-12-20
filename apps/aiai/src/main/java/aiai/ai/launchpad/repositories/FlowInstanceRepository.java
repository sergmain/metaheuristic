package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.FlowInstance;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("launchpad")
public interface FlowInstanceRepository extends CrudRepository<FlowInstance, Long> {

    @Override
    @Transactional(readOnly = true)
    List<FlowInstance> findAll();

    List<FlowInstance> findByExecStateOrderByCreatedOnAsc(int execSate);

    List<FlowInstance> findByExecState(int execState);

    Slice<FlowInstance> findByFlowId(Pageable pageable, long flowId);

    List<FlowInstance> findByFlowId(long flowId);
}

