package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.LaunchpadAddress;
import org.springframework.context.annotation.Profile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
@Profile("launchpad")
public interface LaunchpadAddressRepository extends CrudRepository<LaunchpadAddress, Long> {
}
