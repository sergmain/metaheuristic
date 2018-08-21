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
@Data
@EqualsAndHashCode(of = {"id", "version"})
public class LogData implements Serializable {
    private static final long serialVersionUID = -6065599957629315147L;

    public void setType(Type type) {
        this.logType = type.typeNumber;
    }

    public enum Type{ ASSEMBLY(1), FEATURE(2), FIT(3), PREDICT(4);

        public int typeNumber;

        Type(int typeNumber) {
            this.typeNumber = typeNumber;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
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
