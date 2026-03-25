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

package ai.metaheuristic.ai.exceptions;

/**
 * Thrown when an output variable declaration violates immutability constraints —
 * i.e., a subprocess tries to create an output variable with the same name as
 * an existing variable in an outer (parent) context, and the variable is not
 * declared as mutable.
 *
 * @author Sergio Lissner
 * Date: 3/25/2026
 */
public class VariableImmutabilityException extends RuntimeException {

    public final String variableName;
    public final String outerContextId;
    public final String currentContextId;

    public VariableImmutabilityException(String message, String variableName, String outerContextId, String currentContextId) {
        super(message);
        this.variableName = variableName;
        this.outerContextId = outerContextId;
        this.currentContextId = currentContextId;
    }
}
