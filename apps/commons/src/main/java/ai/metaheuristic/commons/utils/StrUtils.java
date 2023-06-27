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
package ai.metaheuristic.commons.utils;

import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.nio.file.Path;
import java.util.Random;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
@Slf4j
public class StrUtils {

    private static final String COPY_NUMBER_PREFIX = "Copy #";
    public static final String ALLOWED_CHARS_IN_CODE_REGEXP = "^[A-Za-z0-9.:_-]*$";
    public static final String ALLOWED_CHARS_IN_VAR_NAME_REGEXP = "^[A-Za-z_][A-Za-z0-9_]*$";

    private static final Pattern codePattern = Pattern.compile(ALLOWED_CHARS_IN_CODE_REGEXP);
    private static final Pattern varNamePattern = Pattern.compile(ALLOWED_CHARS_IN_VAR_NAME_REGEXP);

    private static final char[] ALPHA_NUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final Random RANDOM = new Random();

    public static String randomAlphanumeric(final int count) {
        final StringBuilder strBuilder = new StringBuilder(count);
        final int anLen = ALPHA_NUMERIC.length;
        for (int i = 0; i < count; i++) {
            strBuilder.append(ALPHA_NUMERIC[RANDOM.nextInt(anLen)]);
        }
        return strBuilder.toString();
    }

    public static boolean isCodeOk(String code) {
        Matcher m = codePattern.matcher(code);
        return m.matches();
    }

    public static boolean isVarNameOk(String name) {
        Matcher m = varNamePattern.matcher(name);
        return m.matches();
    }

    public static String normalizeCode(String code) {
        return StringUtils.replaceEach(code, new String[]{":", ",", " "}, new String[]{"-", "_", "_"});
    }

    public static String normalizeVariable(String code) {
        return StringUtils.replaceEach(code, new String[]{":", ",", " ", "-"}, new String[]{"_", "_", "_", "_"});
    }

    @Nullable
    public static String getExtension(String filename) {
        if (S.b(filename)) {
            return null;
        }
        int idx = filename.lastIndexOf('.');
        return idx!=-1 ? filename.substring(idx) : "";
    }

    public static String getName(Path file) {
        return getName(file.getFileName().toString());
    }

    public static String getName(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx!=-1 ? filename.substring(0, idx) : filename;
    }

    public static String incCopyNumber(String s) {
        if (!s.startsWith(COPY_NUMBER_PREFIX)) {
            return formatString(s, 2);
        }
        final int idx = s.indexOf(',');
        if (idx==-1) {
            return formatString(s, 2);
        }

        String mainPart = s.substring(idx+1).trim();
        try {
            int num = Integer.parseInt(s.substring(COPY_NUMBER_PREFIX.length(), idx).trim());
            return formatString(mainPart, num+1);
        }
        catch(NumberFormatException e) {
            log.warn("Error while incrementing copy number for string '{}'", s);
        }
        return formatString(mainPart, 2);
    }

    private static String formatString(String s, int i) {
        return COPY_NUMBER_PREFIX + i +", " + s;
    }

    // this method is for getting code for SourceCodeParamsYaml.uid or for SourceCodeParamsYaml.Process.code
    public static String getCode(String name, Supplier<String> defaultNameFunc) {
        String code;
        if (isCodeOk(name)) {
            code = name;
        }
        else {
            String n = normalizeCode(name);
            code = isCodeOk(n) ? n : defaultNameFunc.get();
        }
        return code;
    }

    public static String getVariableName(String name, Supplier<String> defaultNameFunc) {
        String code;
        if (isVarNameOk(name)) {
            code = name;
        }
        else {
            String n = normalizeVariable(name);
            code = isVarNameOk(n) ? n : defaultNameFunc.get();
        }
        return code;
    }
}
