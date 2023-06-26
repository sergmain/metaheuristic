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

package ai.metaheuristic.ai.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 2/11/2021
 * Time: 4:30 PM
 */
@Slf4j
public class ErrorUtils {

    public static String getAllMessages(Throwable th) {
        return getAllMessages(th, 0);
    }

    public static String getAllMessages(Throwable th, int skip) {
        final List<Throwable> throwableList = ExceptionUtils.getThrowableList(th);
        if (skip!=0) {
            if (throwableList.size()>skip) {
                return throwableList.stream().skip(skip).map(Throwable::getMessage).collect(Collectors.joining(". "));
            }
            log.warn("#714.020 The value of skip is greater or equal to length of causes of Throwable. The list of cause will be returned without skipping of any element");
        }

        return throwableList.stream().map(Throwable::getMessage).collect(Collectors.joining(". "));
    }
}
