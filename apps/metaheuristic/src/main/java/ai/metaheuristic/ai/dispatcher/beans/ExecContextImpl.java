/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.api.dispatcher.ExecContext;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Entity
@Table(name = "MH_EXEC_CONTEXT")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ExecContextImpl implements Serializable, ExecContext {
    private static final long serialVersionUID = -8687758209537096490L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "SOURCE_CODE_ID")
    public Long sourceCodeId;

    // This field contains a value from MH_COMPANY.UNIQUE_ID, !NOT! from ID field
    @NotNull
    @Column(name = "COMPANY_ID")
    public Long companyId;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Nullable
    @Column(name="COMPLETED_ON")
    public Long completedOn;

    @NotBlank
    @Column(name = "PARAMS")
    public String params;

    @NonNull
    public String getParams() {
        return params;
    }

    @Column(name = "IS_VALID")
    public boolean valid;

    @Column(name = "STATE")
    public int state;
}