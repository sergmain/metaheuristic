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

package ai.metaheuristic.ai.mhbp.yaml.chat_log;

import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("FieldMayBeStatic")
@Data
public class ChatLogParams implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        if (api==null) {
            throw new CheckIntegrityFailedException("(api==null)");
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {
        public long apiId;
        public String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Prompt {
        // prompt
        public String p;
        // answer
        public String a;
        // raw result
        public String r;
        // error
        public String e;
    }

    public Api api;
    public Prompt prompt;

    @Nullable
    public Long scenarioId;

    @Nullable
    public Long chatId;

    public boolean stateless;
}
