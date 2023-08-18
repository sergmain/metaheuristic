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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * @author Sergio Lissner
 * Date: 8/17/2023
 * Time: 11:52 AM
 */
@Service
@Profile({"dispatcher & disk-storage"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DiskBlobStorageService implements DispatcherBlobStorage {

    @Override
    public void accessVariableData(Long variableBlobId, Consumer<InputStream> processBlobDataFunc) {

    }

    @Override
    public InputStream getVariableDataAsStreamById(Long variableBlobId) {
        return null;
    }

    @Override
    public void storeVariableData(Long variableBlobId, InputStream is, long size) {

    }

    @Override
    public void copyVariableData(VariableData.StoredVariable srcVariable, TaskParamsYaml.OutputVariable targetVariable) {

    }

    @Override
    public InputStream getGlobalVariableDataAsStreamById(Long globalVariableId) {
        return null;
    }

    @Override
    public void accessGlobalVariableData(Long globalVariableId, Consumer<InputStream> processBlobDataFunc) throws SQLException, IOException {

    }

    @Override
    public void storeGlobalVariableData(Long globalVariableId, InputStream is, long size) {

    }

    @Override
    public void accessFunctionData(String functionCode, Consumer<InputStream> processBlobDataFunc) throws SQLException, IOException {

    }

    @Override
    public void storeFunctionData(Long functionDataId, InputStream is, long size) {

    }
}
