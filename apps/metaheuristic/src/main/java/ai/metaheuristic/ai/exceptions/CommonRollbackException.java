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

package ai.metaheuristic.ai.exceptions;

import ai.metaheuristic.api.EnumsApi;

import java.util.ArrayList;
import java.util.List;

import static ai.metaheuristic.api.EnumsApi.OperationStatus.OK;

/**
 * @author Sergio Lissner
 * Date: 9/27/2023
 * Time: 7:58 AM
 */
public class CommonRollbackException extends RuntimeException {
    public final List<String> messages = new ArrayList<>();
    public final EnumsApi.OperationStatus status;

    public CommonRollbackException() {
        this.messages.add("no description for this error");
        this.status = OK;
    }

    public CommonRollbackException(String message, EnumsApi.OperationStatus status) {
        this.messages.add(message);
        this.status = status;
    }

    public CommonRollbackException(List<String> messages, EnumsApi.OperationStatus status) {
        this.messages.addAll(messages);
        this.status = status;
    }

    public static CommonRollbackException asOk() {
        return new CommonRollbackException("ok", OK);
    }
}
