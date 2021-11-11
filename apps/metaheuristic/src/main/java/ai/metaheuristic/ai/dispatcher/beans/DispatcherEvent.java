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
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 8:21 PM
 */
@Entity
@Table(name = "MH_EVENT")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class DispatcherEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 6281346638344725952L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    // This field contains a value from MH_COMPANY.UNIQUE_ID, !NOT! from ID field
    @Nullable
    @Column(name = "COMPANY_ID")
    public Long companyId;

    // it was left for backward compatibility
    @Deprecated
    @Column(name="CREATED_ON")
    public long createdOn=0;

    /**
     * значения этого поля имеют формат yyyymm где: yyyy - год, mm - месяц
     */
    @Column(name="PERIOD")
    public int period;

    @Column(name = "EVENT")
    public String event;

    @Column(name = "PARAMS")
    public String params;

}
