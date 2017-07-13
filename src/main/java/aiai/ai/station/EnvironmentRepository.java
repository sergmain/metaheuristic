package aiai.ai.station;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 17:34
 */
@Component
@Transactional
public interface EnvironmentRepository extends CrudRepository<Env, Long> {

    @Transactional(readOnly = true)
    Slice<Env> findAll(Pageable pageable);
}
