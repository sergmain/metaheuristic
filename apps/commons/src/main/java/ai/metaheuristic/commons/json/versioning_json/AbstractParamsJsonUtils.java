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

package ai.metaheuristic.commons.json.versioning_json;

import ai.metaheuristic.api.data.BaseParams;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Serge
 * Date: 4/16/2021
 * Time: 5:18 PM
 */
abstract public class AbstractParamsJsonUtils<CurrT extends BaseParams, NextT extends BaseParams, NextU, PrevT, PrevU, CurrForDownT> {

    @Nonnull
    public abstract NextT upgradeTo(@Nonnull CurrT baseParams);

    @Nonnull
    public abstract PrevT downgradeTo(@Nonnull CurrForDownT baseParams);

    @Nullable
    public abstract NextU nextUtil();

    @Nullable
    public abstract PrevU prevUtil();

    public abstract String toString(CurrT baseParams);

    @Nonnull
    public abstract CurrT to(String s);

    public abstract int getVersion();
}
