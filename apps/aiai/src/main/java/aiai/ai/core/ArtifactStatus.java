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
package aiai.ai.core;

public enum ArtifactStatus {

    NONE(0), OK(1), ERROR(2), OBSOLETE(3);

    public final int value;

    ArtifactStatus(int value) {
        this.value = value;
    }

    public static String byValue(int value) {
        switch(value) {
            case 0:
                return NONE.toString();
            case 1:
                return OK.toString();
            case 2:
                return ERROR.toString();
            case 3:
                return OBSOLETE.toString();
            default:
                throw new IllegalStateException("unknow value: " + value);
        }
    }
}
