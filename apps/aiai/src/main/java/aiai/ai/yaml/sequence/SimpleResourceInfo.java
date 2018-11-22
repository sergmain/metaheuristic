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
package aiai.ai.yaml.sequence;

import aiai.ai.Enums;
import lombok.Data;

@Data
public class SimpleResourceInfo {
    public String id;
    public Enums.BinaryDataType binaryDataType;
    // it's initialized at station side
    public String path;

    public static SimpleResourceInfo of(Enums.BinaryDataType binaryDataType, String id) {
        SimpleResourceInfo sf = new SimpleResourceInfo();
        sf.id = id;
        sf.binaryDataType = binaryDataType;
        return sf;
    }
}
