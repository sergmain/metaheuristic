package aiai.ai.launchpad.experiment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:41
 */
@Component
@Transactional
public interface ExperimentRepository extends CrudRepository<Experiment, Long> {

    @Transactional(readOnly = true)
    Slice<Experiment> findAll(Pageable pageable);
}
