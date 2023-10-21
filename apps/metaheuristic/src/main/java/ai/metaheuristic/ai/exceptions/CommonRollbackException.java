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

package ai.metaheuristic.ai.exceptions;

import ai.metaheuristic.api.EnumsApi;

import static ai.metaheuristic.api.EnumsApi.OperationStatus.OK;

/**
 * @author Sergio Lissner
 * Date: 9/27/2023
 * Time: 7:58 AM
 */
public class CommonRollbackException extends RuntimeException {
    public final String error;
    public final EnumsApi.OperationStatus status;

    public CommonRollbackException() {
        this.error = "no description for this error";
        this.status = OK;
    }

    public CommonRollbackException(String error, EnumsApi.OperationStatus status) {
        this.error = error;
        this.status = status;
    }

    public static CommonRollbackException asOk() {
        return new CommonRollbackException("ok", OK);
    }
}
