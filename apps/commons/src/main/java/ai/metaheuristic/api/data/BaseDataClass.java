/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.api.data;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
public class BaseDataClass {
    public List<String> errorMessages;
    public List<String> infoMessages;

    public void addErrorMessage(String errorMessage) {
        if (this.errorMessages==null) {
            this.errorMessages = new ArrayList<>();
        }
        this.errorMessages.add(errorMessage);
    }

    public void addErrorMessages(List<String> errorMessages) {
        if (this.errorMessages==null) {
            this.errorMessages = new ArrayList<>();
        }
        this.errorMessages.addAll(errorMessages);
    }

    public void addInfoMessage(String infoMessage) {
        if (this.infoMessages==null) {
            this.infoMessages = new ArrayList<>();
        }
        this.infoMessages.add(infoMessage);
    }

    public String getErrorMessagesAsStr() {
        if (!isNotEmpty(errorMessages)) {
            return "";
        }
        if (errorMessages.size()==1) {
            return errorMessages.get(0);
        }
        return errorMessages.toString();
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
