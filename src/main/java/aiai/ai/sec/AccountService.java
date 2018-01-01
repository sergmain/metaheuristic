package aiai.ai.sec;

import aiai.ai.beans.Account;
import aiai.ai.repositories.AccountRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Account getAccount(String username, String password, String token) {
        if (StringUtils.isAnyBlank(username, password, token)) {
            return null;
        }

        Account account = findByUsername(username);
        if (account == null || !token.equals(account.getToken())) {
            return null;
        }

        if (!passwordEncoder.matches(password, account.getPassword())) {
            return null;
        }
        return account;
    }

    @CachePut(cacheNames="byUsername", key = "#account.username")
    public void save(Account account) {
        accountRepository.save(account);
    }

    @Cacheable(cacheNames="byUsername")
    public Account findByUsername(String username) {
        //System.out.println("return non-cached result");
        return  accountRepository.findByUsername(username);
    }
}
