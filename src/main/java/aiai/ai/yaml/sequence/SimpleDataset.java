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

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class SimpleDataset {
    public long id;
    public String path;

    public static SimpleDataset of(String id) {
        return of(Long.parseLong(id));
    }

    public static SimpleDataset of(long id) {
        SimpleDataset sd = new SimpleDataset();
        sd.id = id;
        return sd;
    }
}
