/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.storage;

import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * @author Sergio Lissner
 * Date: 8/17/2023
 * Time: 11:49 AM
 */
public interface DispatcherBlobStorage {

    void accessVariableData(final Long variableBlobId, Consumer<InputStream> processBlobDataFunc) throws SQLException, IOException;

    InputStream getVariableDataAsStreamById(Long variableBlobId);

    void storeVariableData(Long variableBlobId, InputStream is, long size);

    void copyVariableData(VariableData.StoredVariable srcVariable, TaskParamsYaml.OutputVariable targetVariable);

    InputStream getGlobalVariableDataAsStreamById(Long globalVariableId);

    void accessGlobalVariableData(final Long globalVariableId, Consumer<InputStream> processBlobDataFunc) throws SQLException, IOException;

    void storeGlobalVariableData(Long globalVariableId, InputStream is, long size);

}


