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
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * User: Serg
 * Date: 03.08.2017
 * Time: 21:03
 */
@Entity
@Table(name = "AIAI_LP_DATASET_PATH")
@Data
@EqualsAndHashCode(exclude = {"dataset"})
public class DatasetPath implements Serializable {
    private static final long serialVersionUID = 5327272240646790910L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "PATH_NUMBER")
    private int pathNumber;

    @Column(name = "PATH")
    private String path;

    @Column(name = "REG_TS")
    private Timestamp registerTs;

    @Column(name = "CHECKSUM")
    private String checksum;

    @Column(name = "IS_FILE")
    private boolean isFile;

    @Column(name = "IS_VALID")
    private boolean isValid;

//    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DATASET_ID")
    private Dataset dataset;

}
