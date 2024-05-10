/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.commons.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

@Entity
@Table(name = "MH_VARIABLE")
@Data
@EqualsAndHashCode(of = {"id", "version"})
@NoArgsConstructor
@AllArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Variable implements Serializable {
    @Serial
    private static final long serialVersionUID = 7768428475142175426L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name = "IS_INITED")
    public boolean inited;

    @Column(name = "IS_NULLIFIED")
    public boolean nullified;

    @Nullable
    @Column(name = "VARIABLE_BLOB_ID")
    public Long variableBlobId;

    @Column(name = "NAME")
    public String name;

    @Column(name = "EXEC_CONTEXT_ID")
    public Long execContextId;

    @Column(name = "TASK_CONTEXT_ID")
    public String taskContextId;

    @Column(name = "UPLOAD_TS")
    public Timestamp uploadTs;

    @Nullable
    @Column(name = "FILENAME")
    public String filename;

    // ai.metaheuristic.api.data_storage.DataStorageParams is here
    @Column(name = "PARAMS")
    private String params;

    // TODO 2020-12-21 need to add a way to check the length of variable with length of stored on disk variable
    //  maybe even with checksum
    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.paramsLocked.reset(()->this.params = params);
    }

    @Transient
    @JsonIgnore
    private final ThreadUtils.CommonThreadLocker<DataStorageParams> paramsLocked = new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private DataStorageParams parseParams() {
        DataStorageParams temp = DataStorageParamsUtils.UTILS.to(params);
        DataStorageParams ecpy = temp==null ? new DataStorageParams() : temp;
        return ecpy;
    }

    @JsonIgnore
    public DataStorageParams getDataStorageParams() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(DataStorageParams wpy) {
        setParams(DataStorageParamsUtils.UTILS.toString(wpy));
    }

}
