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
package ai.metaheuristic.ai.dispatcher.beans;

import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.paramsLocked.reset(()->this.status = status);
    }

    @Transient
    @JsonIgnore
    private final ThreadUtils.CommonThreadLocker<ProcessorStatusYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ProcessorStatusYaml parseParams() {
        ProcessorStatusYaml temp = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(status);
        ProcessorStatusYaml ecpy = temp==null ? new ProcessorStatusYaml() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ProcessorStatusYaml getProcessorStatusYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(ProcessorStatusYaml dpy) {
        setStatus(ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(dpy));
    }

}

