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

package ai.metaheuristic.ai.mhbp.data;

import ai.metaheuristic.api.data.BaseDataClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.Collections;

/**
 * @author Sergio Lissner
 * Date: 4/19/2023
 * Time: 4:16 PM
 */
public class ErrorData {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        // prompt
        public String p;
        // expected
        public String e;
        // answer
        public String a;
        // raw
        public String r;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleError {
        public Long id;
        public Long sessionId;
        // prompt
        public String p;
        // expected
        public String e;
        // answer
        public String a;
        // raw
        public String r;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ErrorResult extends BaseDataClass {
        public SimpleError error;

        public ErrorResult(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public ErrorResult(SimpleError error, String errorMessage) {
            this.error = error;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public ErrorResult(SimpleError error) {
            this.error = error;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ErrorsResult extends BaseDataClass {
        public Page<SimpleError> errors;

        public ErrorsResult(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }
    }
}
