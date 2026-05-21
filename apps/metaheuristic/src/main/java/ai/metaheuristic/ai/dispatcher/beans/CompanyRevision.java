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

import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYamlUtils;
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
 * Satellite (append-only revision) row for {@link Company}. Carries the mutable
 * scalars (NAME, PARAMS) plus IS_DELETED. Inserted via CompanyRevisionWriter on
 * every Company state change; existing rows are never updated.
 *
 * Contract:
 * - Each row has a unique (COMPANY_ID, REVISION) pair.
 * - Once a row with IS_DELETED=true is inserted for a given COMPANY_ID, no
 *   further rows may be inserted for that COMPANY_ID.
 * - At most one row per COMPANY_ID has IS_DELETED=true, and it is the highest
 *   REVISION for that COMPANY_ID.
 * - When IS_DELETED=true is inserted here, the parent envelope's IS_DELETED
 *   flips to true as part of the same transaction.
 */
@Entity
@Table(name = "MH_COMPANY_REVISION")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class CompanyRevision implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    /** FK to MH_COMPANY.ID (the envelope row). */
    @Column(name = "COMPANY_ID")
    public Long companyId;

    /** Monotonic per-companyId revision number, starting at 1. */
    @Column(name = "REVISION")
    public Long revision;

    @Column(name = "NAME")
    public String name;

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
    private final ThreadUtils.CommonThreadLocker<CompanyParamsYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private CompanyParamsYaml parseParams() {
        if (params == null) {
            return new CompanyParamsYaml();
        }
        CompanyParamsYaml temp = CompanyParamsYamlUtils.BASE_YAML_UTILS.to(params);
        return temp == null ? new CompanyParamsYaml() : temp;
    }

    @JsonIgnore
    public CompanyParamsYaml getCompanyParamsYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(CompanyParamsYaml tpy) {
        setParams(CompanyParamsYamlUtils.BASE_YAML_UTILS.toString(tpy));
    }
}
