/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.commons.account;

import ai.metaheuristic.commons.S;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static ai.metaheuristic.api.data.account.AccountApiData.SerializableGrantedAuthority;

/**
 * @author Serge
 * Date: 8/30/2020
 * Time: 3:30 PM
 */
public class AccountRoles {

    @Data
    public static class InitedRoles {
        public boolean inited;
        public final List<String> roles = new ArrayList<>();

        public void reset() {
            inited = false;
            roles.clear();
        }
        public boolean contains(String role) {
            if (!inited) {
                throw new IllegalStateException("(!inited)");
            }
            return roles.contains(role);
        }

        public void addRole(String role) {
            roles.add(role);
        }

        public void removeRole(String role) {
            roles.remove(role);
        }

        public String asString() {
            return String.join(", ", roles);
        }
    }

    private final Supplier<String> roleGetter;
    private final Consumer<String> roleSetter;

    private final InitedRoles initedRoles = new InitedRoles();
    private final List<SerializableGrantedAuthority> authorities = new ArrayList<>();

    public AccountRoles(Supplier<String> roleGetter, Consumer<String> roleSetter) {
        this.roleSetter = roleSetter;
        this.roleGetter = roleGetter;
    }

    public boolean hasRole(String role) {
        initRoles();
        return initedRoles.contains(role);
    }

    public List<SerializableGrantedAuthority> getAuthorities() {
        initRoles();
        return authorities;
    }

    public List<String> getRolesAsList() {
        initRoles();
        return new ArrayList<>(initedRoles.roles);
    }

    public String asString() {
        initRoles();
        return String.join(",", initedRoles.roles);
    }

    public void addRole(String role) {
        synchronized (this) {
            initedRoles.addRole(role);
            this.roleSetter.accept(initedRoles.asString());
            initedRoles.reset();
            authorities.clear();
            initRoles();
        }
    }

    public void removeRole(String role) {
        synchronized (this) {
            initedRoles.removeRole(role);
            this.roleSetter.accept(initedRoles.asString());
            initedRoles.reset();

            authorities.clear();
            initRoles();
        }
    }

    private void initRoles() {
        if (initedRoles.inited) {
            return;
        }
        synchronized (this) {
            if (initedRoles.inited) {
                return;
            }
            if (this.roleGetter.get()!=null) {
                StringTokenizer st = new StringTokenizer(this.roleGetter.get(), ",");
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    if (S.b(token)) {
                        continue;
                    }
                    String role = fixNaming(token.trim());
                    initedRoles.roles.add(role);
                    authorities.add(new SerializableGrantedAuthority(role));
                }
            }
            initedRoles.inited = true;
        }
    }

    private static String fixNaming(String role) {
        return switch (role) {
            case "ROLE_MASTER_OPERATOR" -> "ROLE_MAIN_OPERATOR";
            case "ROLE_MASTER_SUPPORT" -> "ROLE_MAIN_SUPPORT";
            case "ROLE_MASTER_ASSET_MANAGER" -> "ROLE_MAIN_ASSET_MANAGER";
            default -> role;
        };
    }

}
