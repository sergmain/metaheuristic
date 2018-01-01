package aiai.ai.beans;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

/**
 * User: Serg
 * Date: 12.08.13
 * Time: 23:19
 */
@Entity
@Table(name = "AIAI_ACCOUNT")
@TableGenerator(
        name = "TABLE_AIAI_ACCOUNT",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_ACCOUNT",
        allocationSize = 1,
        initialValue = 1
)
@Data
@EqualsAndHashCode(of={"username", "password", "token"})
public class Account implements UserDetails {

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_ACCOUNT")
    private BigInteger id;
    /**
     * as UUID
     */
    private String username;
    /**
     * as UUID with BCrypt
     */
    private String password;
    private String authorities;

    public Collection<GrantedAuthority> getAuthorities() {
        StringTokenizer st = new StringTokenizer(authorities, ",");
        List<GrantedAuthority> authorityList = new ArrayList<>();
        while (st.hasMoreTokens()) {
            authorityList.add(new SimpleGrantedAuthority(st.nextToken()));
        }
        return authorityList;
    }

    private boolean accountNonExpired;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;
    private boolean enabled;


    private String mailAddress;
    private long phone;

    //TODO add checks on max length
    private String phoneAsStr;


    /**
     * токен для проверки логин/пароля/токена
     */
    private String token;

}
