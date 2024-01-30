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

package ai.metaheuristic.ww2003.document;

import ai.metaheuristic.ww2003.document.style.InnerStyles;

/**
 * @author Serge
 * Date: 6/5/2021
 * Time: 9:03 PM
 */
public class ThreadLocalUtils {

    public static final ThreadLocal<InnerStyles> context = new ThreadLocal<>();

    public static InnerStyles getInnerStyles() {
        InnerStyles parsingContext = context.get();
        if (parsingContext==null) {
            parsingContext = new InnerStyles();
            context.set(parsingContext);
        }
        return parsingContext;
    }

    public static void resetInnerStyles() {
        InnerStyles parsingContext = context.get();
        if (parsingContext!=null) {
            parsingContext.destroy();
            context.set(null);
        }
    }

}
