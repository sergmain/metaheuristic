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

package ai.metaheuristic.ww2003.document.exceptions;

import ai.metaheuristic.commons.S;

import java.util.regex.Pattern;

public class DocumentProcessingException extends RuntimeException {

    public DocumentProcessingException(String message) {
        super(message);
        if (S.b(message)) {
            throw new IllegalStateException("000.000 (S.b(message))");
        }
    }

    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("^\\d{3}.\\d{3}$");

    public DocumentProcessingException(String errorCode, UtilsExecutingException e) {
        super(""+ errorCode + ' ' + e.getMessage(), e);
        validate(errorCode, e.getMessage());
    }

    public DocumentProcessingException(String errorCode, DocumentProcessingException e) {
        super(""+ errorCode + ' ' + e.getMessage(), e);
        validate(errorCode, e.getMessage());
    }

    public DocumentProcessingException(String errorCode, Throwable th) {
        super(""+ errorCode + ' ' + getMessage(th), th);
        validate(errorCode, getMessage(th));
    }

    private static String getMessage(Throwable th) {
        final String thMessage = th.getMessage();
        return S.b(thMessage) ? "Exception message is blank, class: " + th.getClass().getName() : thMessage;
    }

    @SuppressWarnings("ConstantConditions")
    private static void validate(String errorCode, String e) {
        if (errorCode ==null || !ERROR_CODE_PATTERN.matcher(errorCode).find()) {
            throw new IllegalStateException("(errorCode==null || !ERROR_CODE_PATTERN.matcher(errorCode).find())");
        }
        if (e==null) {
            throw new IllegalStateException("(e.getMessage()==null)");
        }
    }

}
