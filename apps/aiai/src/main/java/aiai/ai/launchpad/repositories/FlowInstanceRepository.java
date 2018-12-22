package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.FlowInstance;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Repository
@Profile("launchpad")
@Transactional
public interface FlowInstanceRepository extends CrudRepository<FlowInstance, Long> {

    @Transactional(readOnly = true)
    @Query(value="select f from FlowInstance f")
    Stream<FlowInstance> findAllAsStream();

    List<FlowInstance> findByExecStateOrderByCreatedOnAsc(int execSate);

    List<FlowInstance> findByExecState(int execState);

    Slice<FlowInstance> findByFlowId(Pageable pageable, long flowId);

    List<FlowInstance> findByFlowId(long flowId);
}

