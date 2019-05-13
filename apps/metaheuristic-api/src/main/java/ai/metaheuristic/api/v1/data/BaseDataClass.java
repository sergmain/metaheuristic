/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.api.v1.data;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
public class BaseDataClass {
    public List<String> errorMessages;
    public List<String> infoMessages;

    public void addErrorMessage(String errorMessage) {
        if (errorMessages==null) {
            errorMessages = new ArrayList<>();
        }
        errorMessages.add(errorMessage);
    }

    public void addInfoMessage(String infoMessage) {
        if (infoMessages==null) {
            infoMessages = new ArrayList<>();
        }
        infoMessages.add(infoMessage);
    }

    public boolean isErrorMessages() {
        return isNotEmpty(errorMessages);
    }

    public boolean isInfoMessages() {
        return isNotEmpty(infoMessages);
    }

    private static boolean isNotEmpty(Collection<?> collection) {
        return collection!=null && !collection.isEmpty();
    }
}
