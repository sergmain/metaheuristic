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

package ai.metaheuristic.ai.mhbp.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.hc.core5.http.NameValuePair;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 9:17 PM
 */
public class CommunicationData {

    @Data
    public static class RegisterDone {
        boolean success;
        int code;
        String msg;

        public String username;

        public RegisterDone(boolean success, int code) {
            this.success = success;
            this.code = code;
        }

        public RegisterDone(boolean success, int code, String username) {
            this.success = success;
            this.code = code;
            this.username = username;
        }
    }

    @Data
    public static class RegisterResult {
        boolean success;
        int code;
        int extendedCode;
        String msg;

        public RegisterResult(boolean success, int code) {
            this.success = success;
            this.code = code;
        }

        public RegisterResult(boolean success, int code, int extendedCode, String msg) {
            this.success = success;
            this.code = code;
            this.extendedCode = extendedCode;
            this.msg = msg;
        }

        public RegisterResult(boolean success, int code, String msg) {
            this.success = success;
            this.code = code;
            this.msg = msg;
        }
    }

    @Data
    @AllArgsConstructor
    public static class Query {
        public final String url;
        public final List<NameValuePair> nvps;
    }
}
