package aiai.ai.sec;

import aiai.ai.beans.Account;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Serg
 * Date: 12.08.13
 * Time: 23:17
 */
@Service
public class CustomUserDetails implements UserDetailsService {

    @Value("${aiai.master-username}")
    private String masterUsername;

    @Value("${aiai.master-token}")
    private String masterToken;

    @Value("${aiai.master-password}")
    private String masterPassword;

    private final AccountService accountService;

    @Autowired
    public CustomUserDetails(AccountService accountService) {
        this.accountService = accountService;
    }

    @Data
    public static class ComplexUsername {
        String username;
        String token;

        private ComplexUsername(String username, String token) {
            this.username = username;
            this.token = token;
        }

        public static ComplexUsername getInstance(String fullUsername) {
            int idx = fullUsername.lastIndexOf('=');
            if (idx==-1) {
                return null;
            }
            ComplexUsername complexUsername = new ComplexUsername(fullUsername.substring(0, idx), fullUsername.substring(idx + 1));

            return complexUsername.isValid() ?complexUsername :null;
        }

        private boolean isValid() {
            return username.length() > 0 && token.length() > 0;
        }
    }
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // strength - 10
        // pass     - 123
        // bcrypt   - $2a$10$jaQkP.gqwgenn.xKtjWIbeP4X.LDJx92FKaQ9VfrN2jgdOUTPTMIu

        ComplexUsername complexUsername = ComplexUsername.getInstance(username);
        if (complexUsername==null) {
            throw new UsernameNotFoundException("Username not found");
        }

        if (StringUtils.equals(masterUsername,complexUsername.getUsername()) && StringUtils.equals(masterToken,complexUsername.getToken())) {

            Account account = new Account();

            account.setId( BigInteger.ONE );
            account.setUsername(masterUsername);
            account.setToken(masterToken);
            account.setAccountNonExpired(true);
            account.setAccountNonLocked(true);
            account.setCredentialsNonExpired(true);
            account.setEnabled(true);
            account.setPassword(masterPassword);

            account.setAuthorities("ROLE_ADMIN, ROLE_MANAGER");
/*
            List<GrantedAuthority> authList = new ArrayList<>();
            authList.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            authList.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
            account.setAuthorities(authList);
*/

            return account;
        }

        Account account = accountService.findByUsername(complexUsername.getUsername());
        if (account==null) {
            throw new UsernameNotFoundException("Username not found");
        }
        if (!complexUsername.getToken().equals(account.getToken())) {
            throw new UsernameNotFoundException("Username not found");
        }
        return account;
    }

}
