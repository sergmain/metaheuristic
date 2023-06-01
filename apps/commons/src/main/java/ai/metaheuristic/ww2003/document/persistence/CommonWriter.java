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

package ai.metaheuristic.ww2003.document.persistence;

import ai.metaheuristic.ww2003.document.CDNode;
import lombok.With;

import java.io.Writer;

/**
 * @author Serge
 * Date: 5/9/2022
 * Time: 9:44 AM
 */
public interface CommonWriter {

    Context DEFAULT_CTX = new Context();

    record OneLevelContext(boolean needEmptyParaAfter) {}

    @With
    class Context {
        public final boolean insideTable;

        public Context() {
            this.insideTable = false;
        }

        public Context(boolean insideTable) {
            this.insideTable = insideTable;
        }

        public Context(boolean insideTable, boolean insideFmt) {
            this.insideTable = insideTable;
        }
    }

    void write(Context context, CDNode cdNode, Writer writer);
}
