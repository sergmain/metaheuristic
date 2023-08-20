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

package ai.metaheuristic.ai.sec;

import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * @author Sergio Lissner
 * Date: 8/18/2023
 * Time: 1:35 AM
 */
@Data
public class ComplexUsername {
    String username;

    private ComplexUsername(String username) {
        this.username = username;
    }

    @Nullable
    public static ComplexUsername getInstance(String fullUsername) {
        int idx = fullUsername.lastIndexOf('=');
        final String username;
        if (idx == -1) {
            username = fullUsername;
        } else {
            username = fullUsername.substring(0, idx);
        }
        ComplexUsername complexUsername = new ComplexUsername(username);

        return complexUsername.isValid() ? complexUsername : null;
    }

    private boolean isValid() {
        return username != null && !username.isBlank();
    }
}
