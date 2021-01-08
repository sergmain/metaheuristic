/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class Interpreter {

    public final String interpreter;
    public final String[] list;

    public Interpreter(String interpreter) {
        if (StringUtils.isBlank(interpreter)) {
            this.interpreter = null;
            this.list = null;
            return;
        }
        this.interpreter = interpreter;
        this.list = StringUtils.split(interpreter, " ");
    }

}
