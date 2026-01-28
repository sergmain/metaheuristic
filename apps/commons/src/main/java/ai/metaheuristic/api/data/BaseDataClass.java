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

package ai.metaheuristic.api.data;

import ai.metaheuristic.api.EnumsApi;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;


/**
 * !!! To include fields from this BaseDataClass class, constructor must be annotated with @JsonCreator
 * @see OperationStatusRest#OperationStatusRest(EnumsApi.OperationStatus, List, List)
 */
@Data
public abstract class BaseDataClass {

    @JsonInclude(value= JsonInclude.Include.NON_NULL, content= JsonInclude.Include.NON_EMPTY)
    public @Nullable List<String> errorMessages;

    @JsonInclude(value= JsonInclude.Include.NON_NULL, content= JsonInclude.Include.NON_EMPTY)
    public @Nullable List<String> infoMessages;

    public void addErrorMessage(@Nullable String errorMessage) {
        if (errorMessage==null) {
            return;
        }
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

    public void addInfoMessages(List<String> infoMessages) {
        if (this.infoMessages==null) {
            this.infoMessages = new ArrayList<>();
        }
        this.infoMessages.addAll(infoMessages);
    }

    @JsonIgnore
    public String getInfoMessagesAsStr() {
        if (!isNotEmpty(infoMessages)) {
            return "";
        }
        if (infoMessages.size()==1) {
            return Objects.requireNonNull(infoMessages.get(0));
        }
        return Objects.requireNonNull(infoMessages.toString());
    }

    @JsonIgnore
    public String getErrorMessagesAsStr() {
        if (!isNotEmpty(errorMessages)) {
            return "";
        }
        if (errorMessages.size()==1) {
            return Objects.requireNonNull(errorMessages.get(0));
        }
        return Objects.requireNonNull(errorMessages.toString());
    }

    @JsonIgnore
    public List<String> getErrorMessagesAsList() {
        return isNotEmpty(errorMessages) ? errorMessages : List.of();
    }

    @JsonIgnore
    public List<String> getInfoMessagesAsList() {
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
