package aiai.ai.launchpad;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;

import javax.transaction.Transactional;

/**
 * User: Serg
 * Date: 15.06.2017
 * Time: 19:53
 */

@Transactional
public interface DatasetsRepository extends CrudRepository<Datasets, Long> {

    Slice<Datasets> findAll(Pageable pageable);
/*

    List<Datasets> findByLastname(String lastname, Sort sort);

    List<Datasets> findByLastname(String lastname, Pageable pageable);

    Long deleteByLastname(String lastname);

    List<Datasets> removeByLastname(String lastname);

*/
}