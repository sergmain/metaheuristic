/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYamlUtils;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYaml;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "MH_SNIPPET")
@Data
public class Snippet implements Serializable {
    private static final long serialVersionUID = 4066977399166436522L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name = "SNIPPET_CODE")
    public String code;

    @Column(name = "SNIPPET_TYPE")
    public String type;

    @Column(name = "PARAMS")
    public String params;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.sc=null;
        }
    }

    public String getParams() {
        return params;
    }

    @Transient
    @JsonIgnore
    private SnippetConfigYaml sc = null;

    @JsonIgnore
    public SnippetConfigYaml getSnippetConfig(boolean isClone) {
        if (sc==null) {
            synchronized (this) {
                if (sc==null) {
                    //noinspection UnnecessaryLocalVariable
                    SnippetConfigYaml temp = SnippetConfigYamlUtils.BASE_YAML_UTILS.to(params);
                    sc = temp;
                    return sc;
                }
            }
        }
        return isClone ? sc.clone() : sc;
    }

    public void reset() {
        synchronized (this) {
            sc = null;
        }
    }

}
