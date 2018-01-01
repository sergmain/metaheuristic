package aiai.ai.repositories;

import aiai.ai.beans.Account;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:41
 */
@Repository
@Transactional
public interface AccountRepository extends CrudRepository<Account, BigInteger> {

    Account findByUsername(String username);
    Account findByMailAddress(String mailAddress);

/*
    @Transactional(readOnly = true)
    Optional<Account> findById(BigInteger id);

    List<Message> findByLocationNear(Point p, Distance d);
*/

}
