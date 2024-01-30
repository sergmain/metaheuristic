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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Serge
 * Date: 3/25/2021
 * Time: 11:31 AM
 */
public class ExperimentStoringException extends RuntimeException {

    public EnumsApi.OperationStatus status;
    @Nullable
    public List<String> errorMessages;

    @Nullable
    public List<String> infoMessages;

    public ExperimentStoringException(EnumsApi.OperationStatus status, String errorMessage) {
        super();
        this.status = status;
        this.errorMessages = Collections.singletonList(errorMessage);
    }

    public ExperimentStoringException(EnumsApi.OperationStatus status, @Nullable String infoMessage, @Nullable String errorMessage) {
        super();
        this.status = status;
        if (!S.b(infoMessage)) {
            this.infoMessages = List.of(infoMessage);
        }
        if (!S.b(errorMessage)) {
            this.errorMessages = List.of(errorMessage);
        }
    }

}
