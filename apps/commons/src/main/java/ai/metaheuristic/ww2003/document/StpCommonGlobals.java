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

package ai.metaheuristic.ww2003.document;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Sergio Lissner
 * Date: 9/2/2022
 * Time: 2:35 AM
 */
public class StpCommonGlobals {

    //
    public static final AtomicBoolean fmtTabAllowed = new AtomicBoolean(false);


    public static final AtomicBoolean onlyStrictLuAllowed = new AtomicBoolean(true);


    public static final AtomicBoolean onlyOneOglAllowed = new AtomicBoolean(true);


    public static final AtomicBoolean zeroLuNumberRestricted = new AtomicBoolean(true);


}
