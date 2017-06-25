package aiai.ai.launchpad;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * User: Serg
 * Date: 25.06.2017
 * Time: 15:52
 */
@Component
@Transactional
public interface StationsRepository extends CrudRepository<Station, Long> {

    @Transactional(readOnly = true)
    Slice<Station> findAll(Pageable pageable);

//    Station save(Station account);

/*

    List<Datasets> findByLastname(String lastname, Sort sort);

    List<Datasets> findByLastname(String lastname, Pageable pageable);

    Long deleteByLastname(String lastname);

    List<Datasets> removeByLastname(String lastname);

*/
}