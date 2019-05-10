/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.api.v1.data;

import aiai.api.v1.EnumsApi;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class OperationStatusRest extends BaseDataClass {

    public static final OperationStatusRest OPERATION_STATUS_OK = new OperationStatusRest(EnumsApi.OperationStatus.OK);
    public EnumsApi.OperationStatus status;

    public OperationStatusRest(EnumsApi.OperationStatus status) {
        this.status = status;
    }

    public OperationStatusRest(EnumsApi.OperationStatus status, List<String> errorMessages) {
        this.status = status;
        this.errorMessages = errorMessages;
    }

    public OperationStatusRest(EnumsApi.OperationStatus status, String errorMessage) {
        this.status = status;
        this.errorMessages = Collections.singletonList(errorMessage);
    }

    public OperationStatusRest(EnumsApi.OperationStatus status, String infoMessage, String errorMessage) {
        this.status = status;
        if (infoMessage!=null) {
            this.infoMessages = List.of(infoMessage);
        }
        if (errorMessage!=null) {
            this.errorMessages = List.of(errorMessage);
        }
    }

}
