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

package ai.metaheuristic.ai.dispatcher.beans;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

@Entity
@Table(name = "MH_LOG_DATA")
@Data
@EqualsAndHashCode(of = {"id", "version"})
@NoArgsConstructor
public class LogData implements Serializable {
    private static final long serialVersionUID = -6065599957629315147L;

    public void setType(Type type) {
        this.logType = type.typeNumber;
    }

    public enum Type{ ASSEMBLING(1), FEATURE(2), FIT(3), PREDICT(4), SEQUENCE(5), PRODUCING(6);

        public int typeNumber;

        Type(int typeNumber) {
            this.typeNumber = typeNumber;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "REF_ID")
    public Long refId;

    @Column(name = "UPDATE_TS")
    public Timestamp updateTs;

    @Column(name = "LOG_DATA")
    public String logData;

    @Column(name = "LOG_TYPE")
    public int logType;

}
