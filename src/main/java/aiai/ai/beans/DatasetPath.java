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

import aiai.ai.beans.Dataset;
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
@TableGenerator(
        name = "TABLE_AIAI_LP_DATASET_PATH",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_LP_DATASET_PATH",
        allocationSize = 1,
        initialValue = 1
)
@Data
@EqualsAndHashCode(exclude = {"dataset"})
public class DatasetPath implements Serializable {
    private static final long serialVersionUID = 5327272240646790910L;
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_LP_DATASET_PATH")
    private Long id;

    /*
        CREATE TABLE AIAI_LP_DATASET_PATH (
                ID          NUMERIC(10, 0) NOT NULL,
        DATASET_ID  NUMERIC(10, 0) NOT NULL,
        VERSION     NUMERIC(5, 0)  NOT NULL,
        FILE_NUMBER NUMERIC(3, 0) NOT NULL,
        PATH        VARCHAR(100),
        REG_TS      TIMESTAMP DEFAULT 0 ON UPDATE CURRENT_TIMESTAMP,
        CHECKSUM    VARCHAR(200),
        IS_FILE     tinyint(1) not null default 1,
        IS_VALID     tinyint(1) not null default 0
                );
    */
    @Version
    @Column(name = "VERSION")
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
    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "DATASET_ID")
    private Dataset dataset;

    public DatasetPath() {
    }
}
