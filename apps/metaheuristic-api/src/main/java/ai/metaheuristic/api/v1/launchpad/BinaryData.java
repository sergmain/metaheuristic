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

package ai.metaheuristic.api.v1.launchpad;

import ai.metaheuristic.api.v1.EnumsApi;

/**
 * @author Serge
 * Date: 5/9/2019
 * Time: 3:18 PM
 */
public interface BinaryData {
    void setType(EnumsApi.BinaryDataType binaryDataType);

    Long getId();

    Integer getVersion();

    String getCode();

    String getPoolCode();

    int getDataType();

    Long getRefId();

    java.sql.Timestamp getUploadTs();

    java.sql.Blob getData();

    String getChecksum();

    boolean isValid();

    boolean isManual();

    String getFilename();

    byte[] getBytes();

    String getParams();

    void setId(Long id);

    void setVersion(Integer version);

    void setCode(String code);

    void setPoolCode(String poolCode);

    void setDataType(int dataType);

    void setRefId(Long refId);

    void setUploadTs(java.sql.Timestamp uploadTs);

    void setData(java.sql.Blob data);

    void setChecksum(String checksum);

    void setValid(boolean valid);

    void setManual(boolean manual);

    void setFilename(String filename);

    void setBytes(byte[] bytes);

    void setParams(String params);

    String getRefType();

    void setRefType(String refType);
}
