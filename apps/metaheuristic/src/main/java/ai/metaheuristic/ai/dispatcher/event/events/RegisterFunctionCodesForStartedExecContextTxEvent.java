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

package ai.metaheuristic.ai.dispatcher.event.events;

import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.springframework.lang.Nullable;

/**
 * @author Sergio Lissner
 * Date: 11/16/2023
 * Time: 11:12 PM
 */
public record RegisterFunctionCodesForStartedExecContextTxEvent(@Nullable SourceCodeParamsYaml sc, @Nullable Long execContextId) {
    public RegisterFunctionCodesForStartedExecContextEvent to() {
        return new RegisterFunctionCodesForStartedExecContextEvent(sc, execContextId);
    }
}
