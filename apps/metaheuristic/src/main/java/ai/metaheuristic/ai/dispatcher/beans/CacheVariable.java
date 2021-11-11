/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import lombok.ToString;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Blob;

/**
 * @author Serge
 * Date: 10/27/2020
 * Time: 6:56 PM
 */
@Entity
@Table(name = "MH_CACHE_VARIABLE")
@Data
@EqualsAndHashCode(of = {"cacheProcessId", "variableName"})
@ToString(exclude={"data"})
@NoArgsConstructor
public class CacheVariable implements Serializable {
    @Serial
    private static final long serialVersionUID = -1541017259044013865L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @NotNull
    @Column(name = "CACHE_PROCESS_ID")
    public Long cacheProcessId;

    @Column(name="CREATED_ON")
    public long createdOn;

    @NotNull
    @NotEmpty
    @Column(name = "VARIABLE_NAME")
    public String variableName;

    @Column(name = "IS_NULLIFIED")
    public boolean nullified;

    @Nullable
    @Column(name = "DATA")
    @Lob
    public Blob data;

}
