/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
package ai.metaheuristic.ai.launchpad.beans;

import ai.metaheuristic.ai.yaml.workbook.WorkbookParamsYamlUtils;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.launchpad.Workbook;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "MH_WORKBOOK")
@Data
public class WorkbookImpl implements Serializable, Workbook {
    private static final long serialVersionUID = -8687758209537096490L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name = "PLAN_ID")
    public Long planId;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name="COMPLETED_ON")
    public Long completedOn;

    @Column(name = "INPUT_RESOURCE_PARAM")
    private String params;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.wpy=null;
        }
    }

    public String getParams() {
        return params;
    }

    @Column(name = "IS_VALID")
    public boolean valid;

    @Column(name = "EXEC_STATE")
    public int execState;

    // this field will be left only for compatibility, producingOrder isn't used any more
    @Deprecated(forRemoval = true)
    @Column(name = "PRODUCING_ORDER")
    public int producingOrder = 0;

    @Transient
    @JsonIgnore
    private WorkbookParamsYaml wpy = null;

    @JsonIgnore
    public WorkbookParamsYaml getWorkbookParamsYaml() {
        if (wpy ==null) {
            synchronized (this) {
                if (wpy ==null) {
                    //noinspection UnnecessaryLocalVariable
                    WorkbookParamsYaml temp = WorkbookParamsYamlUtils.BASE_YAML_UTILS.to(params);
                    wpy = temp;
                }
            }
        }
        return wpy;
    }

    @JsonIgnore
    public void updateParams(WorkbookParamsYaml wpy) {
        params = WorkbookParamsYamlUtils.BASE_YAML_UTILS.toString(wpy);
    }
}