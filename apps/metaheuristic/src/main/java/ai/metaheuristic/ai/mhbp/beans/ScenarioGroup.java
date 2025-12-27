/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.beans;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author Sergio Lissner
 * Date: 5/4/2023
 * Time: 6:00 PM
 */
@Entity
@Table(name = "MHBP_SCENARIO_GROUP")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ScenarioGroup implements Serializable {
    @Serial
    private static final long serialVersionUID = 7333020817794981901L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

/*
CREATE table mhbp_scenario_group
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    COMPANY_ID      INT UNSIGNED    NOT NULL,
    ACCOUNT_ID      INT UNSIGNED    NOT NULL,
    CREATED_ON      bigint          NOT NULL,
    NAME            VARCHAR(50)     NOT NULL,
    DESCRIPTION     VARCHAR(250)    NOT NULL
);
 */

    @Column(name = "COMPANY_ID")
    public long companyId;

    @Column(name = "ACCOUNT_ID")
    public long accountId;

    @Column(name = "CREATED_ON")
    public long createdOn;

    @Column(name = "NAME")
    public String name;

    @Column(name = "DESCRIPTION")
    public String description;

}