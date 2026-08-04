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
 * Who owns a thing's role set — used in two symmetric places.
 *
 * <p>On a ROLE it answers <b>who may grant this role</b>. On an ACCOUNT it
 * answers <b>who owns this account's role set</b>. Both questions need
 * answering and neither implies the other:
 * <ul>
 *   <li>Role-side only: an admin cannot grant the managed role, but can still
 *       add OTHER roles to a managed account — the account widens while its
 *       managed role sits untouched.</li>
 *   <li>Account-side only: managed accounts are safe, but an admin can create a
 *       fresh account and hand it the managed role, producing something that
 *       works exactly like a managed account with no record of where it came
 *       from. That is a provenance hole rather than a privilege one, which is
 *       worse in an audit because nothing looks wrong.</li>
 * </ul>
 *
 * <p><b>An enum, deliberately not a String.</b> A string sitting beside
 * {@code ROLE_*} names invites being read as a role, and this is not a role: a
 * managing mechanism is system code, not an authenticated principal holding an
 * authority. The compiler now prevents that reading.
 *
 * <p><b>An owner, deliberately not a boolean.</b> A flag like {@code locked}
 * records THAT something is protected without recording WHO may unprotect it.
 * The second mechanism needing the same protection then adds a second boolean
 * and their interaction is undefined. Naming the owner keeps one uniform check
 * for every mechanism that will ever exist: is the caller the owner?
 *
 * <p>Mechanisms are MH-level infrastructure, so this enum is closed to plugins.
 * A plugin contributes ROLES; it does not invent ways of managing them.
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
public enum RoleManager {

    /** Ordinary human administration. The default, and today's only behaviour. */
    admin,

    /**
     * Owned by the communication-channel mechanism: granted only when a channel
     * token is activated, and not editable by hand afterwards.
     */
    commChannel
}
