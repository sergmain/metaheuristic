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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Data
public class BaseDataClass {
    public @Nullable List<String> errorMessages;
    public @Nullable List<String> infoMessages;

    public void addErrorMessage(String errorMessage) {
        if (this.errorMessages==null) {
            this.errorMessages = new ArrayList<>();
        }
        this.errorMessages.add(errorMessage);
    }

    public void addErrorMessages(@NonNull List<String> errorMessages) {
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

    @JsonIgnore
    public @NonNull String getErrorMessagesAsStr() {
        if (!isNotEmpty(errorMessages)) {
            return "";
        }
        if (errorMessages.size()==1) {
            return Objects.requireNonNull(errorMessages.get(0));
        }
        return Objects.requireNonNull(errorMessages.toString());
    }

    @JsonIgnore
    public @NonNull List<String> getErrorMessagesAsList() {
        return isNotEmpty(errorMessages) ? errorMessages : List.of();
    }

    @JsonIgnore
    public @NonNull List<String> getInfoMessagesAsList() {
        return isNotEmpty(infoMessages) ? infoMessages : List.of();
    }

    @JsonIgnore
    public boolean isErrorMessages() {
        return isNotEmpty(errorMessages);
    }

    @JsonIgnore
    public boolean isInfoMessages() {
        return isNotEmpty(infoMessages);
    }

    @JsonIgnore
    private static boolean isNotEmpty(@Nullable Collection<?> collection) {
        return collection!=null && !collection.isEmpty();
    }
}
