/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.commons.account;

/**
 * Which companies a role may be assigned in.
 *
 * <p>The installation already separates two role universes — one for the
 * management company and one for every other company — because the management
 * company runs no business logic. Roles contributed by plugins, however,
 * currently land in BOTH universes with no way to say otherwise, so a role that
 * is meaningless for the management company is still offered there.
 *
 * <p>{@link #all} is the default and reproduces exactly that existing
 * behaviour, so nothing changes for a provider that does not care.
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
public enum RoleScope {

    /** Management company only. */
    company1,

    /**
     * Every company EXCEPT the management one. The right scope for any role
     * that presupposes owning projects or data, since the management company
     * owns neither.
     */
    notCompany1,

    /** Both universes — the default, and the behaviour before this enum existed. */
    all;

    public boolean appliesToCompany1() {
        return this==company1 || this==all;
    }

    public boolean appliesToRegularCompany() {
        return this==notCompany1 || this==all;
    }
}
