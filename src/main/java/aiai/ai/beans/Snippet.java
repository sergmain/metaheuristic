/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.beans;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "AIAI_LP_SNIPPET")
@Data
public class Snippet implements Serializable {
    private static final long serialVersionUID = 4066977399166436522L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "NAME")
    public String name;

    @Column(name = "SNIPPET_TYPE")
    public String type;

    @Column(name = "SNIPPET_VERSION")
    public String snippetVersion;

    @Column(name = "FILENAME")
    public String filename;

    /**
     * programm code, i.e. pyphon, js,...
     */
    @Column(name = "CODE")
    public String code;

    @Column(name = "CHECKSUM")
    public String checksum;

    public String getSnippetCode() {
        return ""+ name + ':' + snippetVersion;
    }
}
