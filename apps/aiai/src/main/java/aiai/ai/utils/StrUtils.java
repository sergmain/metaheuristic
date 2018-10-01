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
package aiai.ai.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StrUtils {
    private static final String COPY_NUMBER_PREFIX = "Copy #";

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
            log.warn("Error while incrimenting copy number for string {}", s);
        }
        return formatString(mainPart, 2);
    }

    private static String formatString(String s, int i) {
        return COPY_NUMBER_PREFIX + i +", " + s;
    }
}
