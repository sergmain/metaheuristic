/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.beans;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.launchpad.BinaryData;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.Timestamp;

@Entity
@Table(name = "MH_DATA")
@Data
@EqualsAndHashCode(of = {"id", "version", "dataType"})
@ToString(exclude={"data", "bytes"})
public class BinaryDataImpl implements Serializable, BinaryData {
    private static final long serialVersionUID = 7768428475142175426L;

    // TODO 2020-01-15 why we have this method?
    //  2020-01-24 so, is it just helper method?
    @Override
    public void setType(EnumsApi.BinaryDataType binaryDataType) {
        this.dataType = binaryDataType.value;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name = "VAR")
    private String variable;

    @Column(name = "DATA_TYPE")
    private int dataType;

    /**
     * This field is initialized only for data resources which were produced while processing workbook.
     * The data resource which is using as input resources must not have workbookId.
     * Also this field is used as refId for deleting any resources which were produced
     * while workbook was processed.
     */
    @Column(name = "WORKBOOK_ID")
    private Long workbookId;

    @Column(name = "CONTEXT_ID")
    private String contextId;

    @Column(name = "UPLOAD_TS")
    private Timestamp uploadTs;

    @Column(name = "DATA")
    @Lob
    private Blob data;

    @Column(name = "FILENAME")
    public String filename;

    @Transient
    public byte[] bytes;

    @Column(name = "PARAMS")
    public String params;

}
