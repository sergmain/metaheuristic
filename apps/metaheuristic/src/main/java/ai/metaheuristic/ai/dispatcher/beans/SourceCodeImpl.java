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

import ai.metaheuristic.commons.yaml.source_code.SourceCodeStoredParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "MH_SOURCE_CODE")
@Data
@NoArgsConstructor
@ToString
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class SourceCodeImpl implements Serializable, SourceCode {
    @Serial
    private static final long serialVersionUID = 6764501814772365639L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "COMPANY_ID")
    public Long companyId;

    @Column(name = "UID")
    public String uid;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Deprecated(forRemoval = true)
    @Column(name = "IS_LOCKED")
    public boolean locked;

    @Column(name = "IS_VALID")
    public boolean valid;

    @Column(name = "PARAMS")
    private String params;

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.paramsLocked.reset(()->this.params = params);
    }

    @Transient
    @JsonIgnore
    private final ThreadUtils.CommonThreadLocker<SourceCodeStoredParamsYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private SourceCodeStoredParamsYaml parseParams() {
        SourceCodeStoredParamsYaml temp = SourceCodeStoredParamsYamlUtils.BASE_YAML_UTILS.to(params);
        SourceCodeStoredParamsYaml ecpy = temp==null ? new SourceCodeStoredParamsYaml() : temp;
        return ecpy;
    }

    @JsonIgnore
    public SourceCodeStoredParamsYaml getSourceCodeStoredParamsYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(SourceCodeStoredParamsYaml scspy) {
        setParams(SourceCodeStoredParamsYamlUtils.BASE_YAML_UTILS.toString(scspy));
    }

}