/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
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

package aiai.ai.launchpad.beans;

import aiai.ai.Enums;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.Timestamp;

@Entity
@Table(name = "AIAI_LP_DATA")
@Data
@EqualsAndHashCode(of = {"id", "version", "dataType"})
@ToString(exclude={"data", "bytes"})
public class BinaryData implements Serializable {
    private static final long serialVersionUID = 7768428475142175426L;

    public void setType(Enums.BinaryDataType binaryDataType) {
        this.dataType = binaryDataType.value;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name = "CODE")
    private String code;

    @Column(name = "POOL_CODE")
    private String poolCode;

    @Column(name = "DATA_TYPE")
    private int dataType;

    /**
     * This field is initialized only for data resources which were produced while processing flow instance.
     * The data resource which is using as input resources must not have flowInstanceId.
     * Also this fiels is used as refId for deleting any resources which were produced
     * while flow instance was processed.
     */
    @Column(name = "FLOW_INSTANCE_ID")
    private Long flowInstanceId;

    @Column(name = "UPLOAD_TS")
    private Timestamp uploadTs;

    @Column(name = "DATA")
    @Lob
    private Blob data;

    @Column(name = "CHECKSUM")
    public String checksum;

    @Column(name = "IS_VALID")
    public boolean valid;

    @Column(name = "IS_MANUAL")
    public boolean manual;

    @Column(name = "FILENAME")
    public String filename;

    @Transient
    public byte[] bytes;

    @Column(name = "STORAGE_URL")
    public String storageUrl;

    public String getDataTypeAsStr() {
        return Enums.BinaryDataType.from(dataType).toString();
    }
}
