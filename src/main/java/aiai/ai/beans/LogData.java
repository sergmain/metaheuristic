/*
 * AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov
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

package aiai.ai.beans;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

@Entity
@Table(name = "AIAI_LOG_DATA")
@TableGenerator(
        name = "TABLE_AIAI_LOG_DATA",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_LOG_DATA",
        allocationSize = 1,
        initialValue = 1
)
@Data
@EqualsAndHashCode(of = {"id", "version"})
public class LogData implements Serializable {
    private static final long serialVersionUID = -6065599957629315147L;

    public void setType(Type type) {
        this.logType = type.typeNumber;
    }

    public enum Type{ ASSEMBLY(1), FEATURE(2);

        public int typeNumber;

        Type(int typeNumber) {
            this.typeNumber = typeNumber;
        }
    }

/*
    CREATE TABLE AIAI_LOG_DATA (
    ID          NUMERIC(10, 0) NOT NULL,
    VERSION     NUMERIC(5, 0)  NOT NULL,
    UPDATE_TS   TIMESTAMP DEFAULT 0 ON UPDATE CURRENT_TIMESTAMP,
    LOG_DATA         MEDIUMTEXT not null
    );
*/

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_LOG_DATA")
    private Long id;

    @Version
    @Column(name = "VERSION")
    private Integer version;

    @Column(name = "REF_ID")
    private Long refId;

    @Column(name = "UPDATE_TS")
    private Timestamp updateTs;

    @Column(name = "LOG_DATA")
    private String logData;

    @Column(name = "LOG_TYPE")
    private int logType;

}
