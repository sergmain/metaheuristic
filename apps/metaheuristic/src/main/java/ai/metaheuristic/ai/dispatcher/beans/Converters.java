/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.beans;

import org.springframework.lang.Nullable;

import javax.persistence.AttributeConverter;

/**
 * @author Sergio Lissner
 * Date: 5/21/2023
 * Time: 3:43 PM
 */
public class Converters {
    public static class IntToBoolConverter implements AttributeConverter<Integer, Boolean> {
        @Override
        public Boolean convertToDatabaseColumn(@Nullable Integer extraFields) {
            if (extraFields==null) {
                //throw new IllegalStateException("(extraFields==null)");
                return false;
            }
            return extraFields==1;
        }
        @Override
        public Integer convertToEntityAttribute(Boolean data) {
            return data ? 1 : 0;
        }
    }


}
