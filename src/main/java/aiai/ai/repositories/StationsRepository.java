package aiai.ai.repositories;

import aiai.ai.launchpad.station.Station;
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

    Station findByIp(String Ip);
}