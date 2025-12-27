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

package ai.metaheuristic.ai.mhbp.beans;

import ai.metaheuristic.ai.mhbp.yaml.part.PartParams;
import ai.metaheuristic.ai.mhbp.yaml.part.PartParamsUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author Sergio Lissner
 * Date: 4/30/2023
 * Time: 2:04 AM
 */
@Entity
@Table(name = "MHBP_PART")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Part implements Serializable {
    @Serial
    private static final long serialVersionUID = 6818687364953026295L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "CHAPTER_ID")
    public long chapterId;

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
    private final ThreadUtils.CommonThreadLocker<PartParams> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private PartParams parseParams() {
        PartParams temp = PartParamsUtils.UTILS.to(params);
        PartParams ecpy = temp==null ? new PartParams() : temp;
        return ecpy;
    }

    @JsonIgnore
    public PartParams getPartParams() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(PartParams apy) {
        setParams(PartParamsUtils.UTILS.toString(apy));
    }

}
