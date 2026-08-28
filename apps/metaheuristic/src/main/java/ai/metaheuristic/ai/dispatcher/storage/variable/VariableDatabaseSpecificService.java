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

package ai.metaheuristic.ai.dispatcher.storage.variable;

import ai.metaheuristic.commons.spi.StoredVariable;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;

/**
 * @author Serge
 * Date: 12/22/2021
 * Time: 10:46 PM
 */
public interface VariableDatabaseSpecificService {
    void copyData(StoredVariable srcVariable, TaskParamsYaml.OutputVariable targetVariable);

    // how a VariableBlob's payload is released is a dialect question: with an in-row LONGBLOB the row
    // delete is the whole of it, while PostgreSQL holds only a pointer and has to unlink the object too
    void delete(Long variableBlobId);
}
