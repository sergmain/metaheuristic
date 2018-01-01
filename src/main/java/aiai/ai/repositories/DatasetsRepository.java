package aiai.ai.repositories;

import aiai.ai.launchpad.dataset.Dataset;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * User: Serg
 * Date: 15.06.2017
 * Time: 19:53
 */
@Component
@Transactional
public interface DatasetsRepository extends CrudRepository<Dataset, Long> {

    @Transactional(readOnly = true)
    Slice<Dataset> findAll(Pageable pageable);

/*

    @Transactional(rollbackFor = {Throwable.class})
    List<Dataset> findByLastname(String lastname, Sort sort);

    List<Dataset> findByLastname(String lastname, Pageable pageable);

    Long deleteByLastname(String lastname);

    List<Dataset> removeByLastname(String lastname);

*/
}