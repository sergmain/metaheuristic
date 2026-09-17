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
package ai.metaheuristic.ai.dispatcher.beans;

import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParams;
import ai.metaheuristic.commons.json.meta_storage.MetaStorageRegistryParamsUtils;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;

/**
 * One row describing ONE meta storage table - what it is for, who wrote it, how to read a record.
 *
 * <p>A meta storage TYPE is an opaque string that exists only because something was written under it.
 * Nothing in MH records what a type means, so a reader enumerating types sees names and nothing more.
 * This table is where that meaning lives.
 *
 * <p>The unique key is {@code (COMPANY_ID, META_TABLE, PROD)}. Each of the three is in it because
 * each one, varied on its own, names a DIFFERENT table:
 *
 * <ul>
 *   <li>{@code COMPANY_ID} - the store is partitioned by company ({@code MH_META_STORAGE} is unique
 *       on {@code (COMPANY_ID, TYPE, REC_KEY)}), so two companies holding a type of the same name
 *       hold two tables over two unrelated sets of records.</li>
 *   <li>{@code META_TABLE} - the type name itself.</li>
 *   <li>{@code PROD} - WHICH store: true means MH_META_STORAGE, false MH_META_STORAGE_SYNTHETIC. The
 *       same type name legitimately exists in both, a capability under development writing the
 *       synthetic one and the same capability in production writing the other.</li>
 * </ul>
 *
 * <p>❗ {@code COMPANY_ID} is therefore part of the identity, not a note about who registered the
 * descriptor. It was once outside the key, and the consequence was that a second company registering
 * an already-used type name overwrote the first company's description in place while COMPANY_ID kept
 * naming the first registrant.
 *
 * <p>There is deliberately NO foreign key to MH_META_STORAGE. A type is not a row there and cannot be
 * referenced as one; it is a column value shared by however many records carry it, and it stops
 * existing when the last of them is deleted. A descriptor therefore outlives its table by construction,
 * which is why removing one is part of dropping a type rather than something the database can enforce.
 *
 * @author Serge
 */
@Entity
@Table(name = "MH_META_STORAGE_REGISTRY")
@Data
@EqualsAndHashCode(of = {"id", "version"})
@ToString(exclude = {"params", "mspl"})
public class MetaStorageRegistry implements Serializable {

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

    @Column(name = "CREATED_ON")
    public long createdOn;

    /** The described type's name - the TYPE column value in whichever store PROD selects. */
    @Column(name = "META_TABLE")
    public String metaTable;

    /** true -> the described table is in MH_META_STORAGE, false -> MH_META_STORAGE_SYNTHETIC. */
    @Column(name = "PROD")
    public boolean prod;

    @Column(name = "PARAMS")
    private String params;

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.mspl = null;
        }
    }

    @Transient
    @Nullable
    private MetaStorageRegistryParams mspl = null;

    public MetaStorageRegistryParams getMetaStorageRegistryParams() {
        if (mspl==null) {
            synchronized (this) {
                if (mspl==null) {
                    mspl = MetaStorageRegistryParamsUtils.BASE_JSON_UTILS.to(params);
                }
            }
        }
        return mspl;
    }

    public void updateParams(MetaStorageRegistryParams p) {
        setParams(MetaStorageRegistryParamsUtils.BASE_JSON_UTILS.toString(p));
    }
}