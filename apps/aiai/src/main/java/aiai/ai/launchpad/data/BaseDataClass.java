/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.data;

import aiai.ai.utils.CollectionUtils;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
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

    public boolean isErrorMessages() {
        return CollectionUtils.isNotEmpty(errorMessages);
    }
}
