/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.beans;

import ai.metaheuristic.ai.yaml.account.AccountParamsYaml;
import ai.metaheuristic.ai.yaml.account.AccountParamsYamlUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;

/**
 * Satellite (append-only revision) row for {@link Account}. Carries the mutable
 * profile/audit scalars (PUBLIC_NAME, MAIL_ADDRESS, PHONE, PHONE_AS_STR,
 * UPDATED_ON, SECRET_KEY, TWO_FA, PARAMS) plus IS_DELETED. Inserted via
 * AccountRevisionWriter on every profile state change; existing rows are
 * never updated.
 *
 * Spring-Security primitives (PASSWORD, IS_ENABLED, ROLES, expired/locked
 * flags) intentionally remain on the envelope {@link Account} for hot-path
 * authentication; revisioning of those will be added in a later extended
 * security-audit task.
 *
 * Contract:
 * - Each row has a unique (ACCOUNT_ID, REVISION) pair.
 * - Once a row with IS_DELETED=true is inserted for a given ACCOUNT_ID, no
 *   further rows may be inserted for that ACCOUNT_ID.
 * - At most one row per ACCOUNT_ID has IS_DELETED=true, and it is the highest
 *   REVISION for that ACCOUNT_ID.
 * - When IS_DELETED=true is inserted here, the parent envelope's IS_DELETED
 *   flips to true as part of the same transaction.
 */
@Entity
@Table(name = "MH_ACCOUNT_REVISION")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class AccountRevision implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    /** FK to MH_ACCOUNT.ID (the envelope row). */
    @Column(name = "ACCOUNT_ID")
    public Long accountId;

    /** Monotonic per-accountId revision number, starting at 1. */
    @Column(name = "REVISION")
    public Long revision;

    @Column(name = "PUBLIC_NAME")
    public String publicName;

    @Nullable
    @Column(name = "MAIL_ADDRESS")
    public String mailAddress;

    @Nullable
    @Column(name = "PHONE")
    public String phone;

    @Nullable
    @Column(name = "PHONE_AS_STR")
    public String phoneAsStr;

    @Column(name = "UPDATED_ON")
    public long updatedOn;

    @Nullable
    @Column(name = "SECRET_KEY")
    public String secretKey;

    @Column(name = "TWO_FA")
    public boolean twoFA;

    @Column(name = "PARAMS")
    private String params;

    @Column(name = "IS_DELETED")
    public boolean deleted;

    @Column(name = "CREATED_ON")
    public long createdOn;

    @Nullable
    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.paramsLocked.reset(() -> this.params = params);
    }

    @Transient
    @JsonIgnore
    private final ThreadUtils.CommonThreadLocker<AccountParamsYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private AccountParamsYaml parseParams() {
        AccountParamsYaml temp = params != null ? AccountParamsYamlUtils.UTILS.to(params) : null;
        return temp == null ? new AccountParamsYaml() : temp;
    }

    @JsonIgnore
    public AccountParamsYaml getAccountParamsYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(AccountParamsYaml tpy) {
        setParams(AccountParamsYamlUtils.UTILS.toString(tpy));
    }
}
