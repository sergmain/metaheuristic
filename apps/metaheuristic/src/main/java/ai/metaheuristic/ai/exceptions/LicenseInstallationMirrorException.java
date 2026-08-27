/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.exceptions;

/**
 * The installation-id mirror file could not be written.
 *
 * <p>Raised by {@code LicenseInstallationService.mirrorToFile}. The cause is carried rather than
 * flattened into the message, because the underlying failure is what distinguishes a permissions
 * denial from a full disk from an unbound {@code mh.home} - and none of that survives
 * {@code getMessage()} alone.
 *
 * @author Serge
 */
public class LicenseInstallationMirrorException extends RuntimeException {

    public LicenseInstallationMirrorException(String message) {
        super(message);
    }

    public LicenseInstallationMirrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
