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

import ai.metaheuristic.ww2003.document.persistence.ww2003.WW2003WritersImpl;

/**
 * @author Serge
 * Date: 6/5/2021
 * Time: 1:19 PM
 */
public class Persistencer {

    private static CommonWriter writers = WW2003WritersImpl.INSTANCE;

    public static void setWriters(CommonWriter writers) {
        Persistencer.writers = writers;
    }


}
