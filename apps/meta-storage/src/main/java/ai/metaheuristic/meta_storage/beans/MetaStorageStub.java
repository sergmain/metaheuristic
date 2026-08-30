/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.meta_storage.beans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;

/**
 * Stub entity on the MAIN datasource - the one Liquibase manages and Hibernate maps.
 *
 * <p>It carries no meta-storage data. Its job is to make the module's persistence stack real: a
 * Liquibase changelog, a mapped entity with {@code @Version} optimistic locking, and a Spring Data
 * repository, all resolving the single auto-configured {@code DataSource}.
 *
 * <p>❗ The meta storage itself does NOT live here. It lives in a SQLite file reached through
 * {@code MetaStorageSpi}, whose connection pool is deliberately not a Spring bean - see
 * {@code MetaStorageConfig}. That separation is the point: two stores, one {@code DataSource} bean,
 * so JPA and Liquibase have exactly one candidate to bind to.
 *
 * @author Serge
 */
@Entity
@Table(name = "META_STORAGE_STUB")
@Data
public class MetaStorageStub implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    public Long id;

    @Version
    @Column(name = "VERSION")
    public Integer version;

    @Column(name = "COMPANY_ID")
    public Long companyId;

    @Column(name = "CODE")
    public String code;

    @Column(name = "PARAMS")
    public String params;

    @Column(name = "CREATED_ON")
    public long createdOn;
}
