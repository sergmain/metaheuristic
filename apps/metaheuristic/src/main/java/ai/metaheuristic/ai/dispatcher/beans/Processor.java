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
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.lang.NonNull;

import javax.persistence.*;
import java.io.Serializable;

/**
 * User: Serg
 * Date: 25.06.2017
 * Time: 15:56
 */
@Entity
@Table(name = "mh_processor")
@Data
@ToString(exclude = "status")
@NoArgsConstructor
public class Processor implements Serializable {
    private static final long serialVersionUID = -6094247705164836600L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NonNull
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
    @Column(name = "IP")
    public String ip;

    @Column(name = "DESCRIPTION")
    public String description;

    @Column(name = "STATUS")
    public String status;

}

