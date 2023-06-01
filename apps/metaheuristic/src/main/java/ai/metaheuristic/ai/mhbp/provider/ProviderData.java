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

package ai.metaheuristic.ai.mhbp.provider;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.api.EnumsApi;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 3/5/2023
 * Time: 1:45 AM
 */
public class ProviderData {

    public static class Provider {
        public String code;
        public String name;
        public String url;
        public String token;
    }

    public static class Providers {
        public static final List<Provider> providers = new ArrayList<>();
    }

    public record QueriedData(String queryText, @Nullable DispatcherContext context){}

    public record QuestionAndAnswer(@Nullable String q, @Nullable String a, EnumsApi.OperationStatus status, @Nullable String error,  @Nullable String raw) {
        public QuestionAndAnswer(EnumsApi.OperationStatus status) {
            this(null, null, status, null, null);
        }

        public QuestionAndAnswer(EnumsApi.OperationStatus status, String error) {
            this(null, null, status, error, null);
        }

        public QuestionAndAnswer(@Nullable String q, @Nullable String a, EnumsApi.OperationStatus status, @Nullable String error, @Nullable String raw) {
            if (status==EnumsApi.OperationStatus.OK) {
                if (raw==null || q==null || a==null) {
                    throw new IllegalStateException("(raw==null || q==null || a==null)");
                }
            }
            this.q = q;
            this.a = a;
            this.status = status;
            this.error = error;
            this.raw = raw;
        }
    }
}
