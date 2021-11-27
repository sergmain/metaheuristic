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

import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.commons.S;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serial;
import java.io.Serializable;

/**
 * User: Serg
 * Date: 25.06.2017
 * Time: 15:56
 */
@Entity
@Table(name = "MH_PROCESSOR")
@Data
@ToString(exclude = "status")
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Processor implements Serializable {
    @Serial
    private static final long serialVersionUID = -6094247705164836600L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Long version;

    /**
     * When status of processor was updated last time
     */
    @Column(name="UPDATED_ON")
    public long updatedOn;

    /**
     * this field is initialized manually
     */
    @Nullable
    @Column(name = "IP")
    public String ip;

    @Nullable
    @Column(name = "DESCRIPTION")
    public String description;

    /**
     * contains data in yaml format
     * @see ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml
     */
    @Column(name = "STATUS")
    private String status;

    public void setStatus(String status) {
        synchronized (this) {
            this.status = status;
            this.psy =null;
        }
    }

    public String getStatus() {
        return status;
    }

    @Transient
    @JsonIgnore
    @Nullable
    private ProcessorStatusYaml psy = null;

    @JsonIgnore
    public ProcessorStatusYaml getProcessorStatusYaml() {
        if (psy ==null) {
            synchronized (this) {
                if (psy ==null) {
                    // to create a valid structure of params
                    String p = S.b(status) ? ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(new ProcessorStatusYaml()) : status;
                    //noinspection UnnecessaryLocalVariable
                    ProcessorStatusYaml temp = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(p);
                    psy = temp;
                }
            }
        }
        return psy;
    }

    @JsonIgnore
    public void updateParams(ProcessorStatusYaml dpy) {
        setStatus(ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(dpy));
    }

}

