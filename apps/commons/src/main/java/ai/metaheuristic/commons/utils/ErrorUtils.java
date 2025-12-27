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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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

    public static String getStackTrace(Throwable e, int numLines, @Nullable String addAtEndLine) {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        try (final PrintStream printStream = new PrintStream(bytesOut, true)) {
            e.printStackTrace(printStream);
        }

        byte[] bytes = bytesOut.toByteArray();
        int countLines = 0;
        int i = 0;
        for (; i < bytes.length; i++) {
            if (bytes[i] == '\n') {
                countLines++;
            }

            if (countLines > numLines) {
                break;
            }
        }

        if (S.b(addAtEndLine)) {
            if (i >= bytes.length) {
                return new String(bytes);
            }
            else {
                return new String(bytes, 0, i);
            }
        }
        else {
            if (i >= bytes.length) {
                return StringUtils.replace(new String(bytes), "\n", addAtEndLine + "\n");
            }
            else {
                return StringUtils.replace(new String(bytes, 0, i), "\n", addAtEndLine + "\n");
            }
        }
    }
}
