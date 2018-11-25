package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.Flow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface FlowRepository  extends CrudRepository<Flow, Long> {

    @Transactional(readOnly = true)
    Slice<Flow> findAll(Pageable pageable);

    Flow findByCode(String code);
}


