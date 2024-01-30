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

package ai.metaheuristic.ai.mhbp.provider;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.mhbp.data.ApiData;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import org.springframework.lang.Nullable;

/**
 * @author Sergio Lissner
 * Date: 3/5/2023
 * Time: 1:45 AM
 */
public class ProviderData {

    public record QueriedData(String queryText, ExecContextData.UserExecContext userExecContext){}

    public record QuestionAndAnswer(@Nullable String q, @Nullable ApiData.QueryResult a, EnumsApi.OperationStatus status, @Nullable String error) {
        public QuestionAndAnswer(EnumsApi.OperationStatus status, String error) {
            this(null, null, status, error);
        }

        public QuestionAndAnswer(@Nullable String q, @Nullable ApiData.QueryResult a) {
            this(q, a, EnumsApi.OperationStatus.OK, null);
            if (S.b(q)) {
                throw new IllegalStateException("(S.b(q))");
            }
        }

    }
}
