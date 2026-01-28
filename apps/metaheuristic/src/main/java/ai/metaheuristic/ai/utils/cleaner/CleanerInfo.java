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

package ai.metaheuristic.ai.utils.cleaner;

import ai.metaheuristic.api.data.BaseDataClass;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 10/2/2019
 * Time: 5:20 PM
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class CleanerInfo extends BaseDataClass {
    public @Nullable ResponseEntity<AbstractResource> entity;
    public List<Path> toClean = new ArrayList<>();
    public List<InputStream> inputStreams = new ArrayList<>();

    @JsonCreator
    public CleanerInfo(
        @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
        @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
        this.errorMessages = errorMessages;
        this.infoMessages = infoMessages;
    }

    public CleanerInfo(String error) {
        addErrorMessage(error);
    }
}
