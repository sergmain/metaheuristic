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

package ai.metaheuristic.ai.exceptions;

/**
 * @author Serge
 * Date: 4/11/2020
 * Time: 5:14 PM
 */
public class FunctionDataErrorException extends CommonErrorWithDataException {

    public String code;

    public FunctionDataErrorException(String code, String message) {
        super(message);
        this.code = code;
    }

    @Override
    public String getAdditionalInfo() {
        return "Function code: " + code;
    }
}
