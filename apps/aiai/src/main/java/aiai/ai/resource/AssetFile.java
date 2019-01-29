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
package aiai.ai.resource;

import lombok.Data;
import lombok.ToString;

import java.io.File;

@Data
public class AssetFile {
    public File file;
    public long fileLength;
    public boolean isError;
    public boolean isContent;
    public boolean isExist;

    @Override
    public String toString() {
        return "AssetFile{" +
                "file=" + file.getPath() +
                ", fileLength=" + fileLength +
                ", isError=" + isError +
                ", isContent=" + isContent +
                ", isExist=" + isExist +
                '}';
    }
}
