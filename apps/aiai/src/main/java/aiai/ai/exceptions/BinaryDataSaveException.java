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

package aiai.ai.exceptions;

/**
 * @author Serge
 * Date: 5/3/2019
 * Time: 1:57 PM
 */
public class BinaryDataSaveException  extends RuntimeException {

    public BinaryDataSaveException(String message) {
        super(message);
    }

    public BinaryDataSaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
