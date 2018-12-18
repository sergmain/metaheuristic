/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.yaml.metadata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

@Data
public class Metadata {

    @Data
    @AllArgsConstructor
    @ToString
    public static class LaunchpadCode {
        public String value;
    }

    public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    public LinkedHashMap<String, LaunchpadCode> launchpad = new LinkedHashMap<>();
}
