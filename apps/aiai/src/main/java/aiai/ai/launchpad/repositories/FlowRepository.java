package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.Flow;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("launchpad")
public interface FlowRepository extends CrudRepository<Flow, Long> {

    @Transactional(readOnly = true)
    Slice<Flow> findAll(Pageable pageable);

    Flow findByCode(String code);
}


