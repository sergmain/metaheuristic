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

package ai.metaheuristic.api.launchpad;

import ai.metaheuristic.api.EnumsApi;

/**
 * @author Serge
 * Date: 5/9/2019
 * Time: 3:18 PM
 */
// We need an interface because of not putting an implementation (which is Entity bean) here
public interface BinaryData {
    void setType(EnumsApi.BinaryDataType binaryDataType);

    Long getId();

    Integer getVersion();

    String getVariable();

    int getDataType();

    Long getWorkbookId();

    java.sql.Timestamp getUploadTs();

    java.sql.Blob getData();

    String getFilename();

    byte[] getBytes();

    String getParams();

    void setId(Long id);

    void setVersion(Integer version);

    void setVariable(String poolCode);

    void setDataType(int dataType);

    void setWorkbookId(Long refId);

    void setUploadTs(java.sql.Timestamp uploadTs);

    void setData(java.sql.Blob data);

    void setFilename(String filename);

    void setBytes(byte[] bytes);

    void setParams(String params);
}
