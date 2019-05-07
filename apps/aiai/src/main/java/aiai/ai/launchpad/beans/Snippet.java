/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.launchpad.beans;

import aiai.apps.commons.yaml.snippet.SnippetUtils;
import aiai.apps.commons.yaml.snippet.SnippetConfig;
import aiai.apps.commons.yaml.snippet.SnippetConfigUtils;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "AIAI_SNIPPET")
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

    @Transient
    private SnippetConfig config = null;

    public SnippetConfig getSnippetConfig() {
        if (config==null) {
            synchronized (this) {
                if (config==null) {
                    //noinspection UnnecessaryLocalVariable
                    SnippetConfig tmp = SnippetConfigUtils.to(params);
                    config = tmp;
                }
            }
        }
        return config;
    }
}
