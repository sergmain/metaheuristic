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
package aiai.ai.launchpad.experiment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.StringTokenizer;

public class ExperimentUtils {

    private static final String RANGE = "range";
    private static final NumberOfVariants ZERO_VARIANT = new NumberOfVariants(true, null, 0);
    private static final NumberOfVariants ONE_VARIANT = new NumberOfVariants(true, null, 1);

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class NumberOfVariants {
        boolean status;
        String error;
        int count;
    }

    public static NumberOfVariants getEpochVariants(String epochs) {
        if (StringUtils.isBlank(epochs)) {
            return ZERO_VARIANT;
        }
        String s = epochs.trim().toLowerCase();
        if (!StringUtils.startsWithAny(s, RANGE, "(", "[")) {
            try {
                int temp = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return new NumberOfVariants(false, "Wrong number format for string: " + s, 0);
            }
            return ONE_VARIANT;
        }
        if (s.startsWith("[")) {
            int count = 0;
            for (StringTokenizer st = new StringTokenizer(s, "[,] "); st.hasMoreTokens(); ) {
                String token = st.nextToken();

                try {
                    int temp = Integer.parseInt(token);
                } catch (NumberFormatException e) {
                    return new NumberOfVariants(false, "Wrong number format for string: " + s, 0);
                }
                count++;
            }
            return new NumberOfVariants(true, null, count);
        }
        String s1 = s;
        if (s1.startsWith(RANGE)) {
            s1 = s1.substring(RANGE.length()).trim();
        }
        if (s1.charAt(0) == '(') {
            Scanner scanner = new Scanner(s1.substring(1));
            scanner.useDelimiter("[,)]");
            int start;
            int end;
            int change;
            try {
                start = Integer.parseInt(scanner.next().trim());
                end = Integer.parseInt(scanner.next().trim());
                change = Integer.parseInt(scanner.next().trim());
            } catch (NumberFormatException | NoSuchElementException e) {
                return new NumberOfVariants(false, "Wrong string format for string: " + s, 0);
            }

            int count = 0;
            for (int i = start; i < end; i += change) {
                count++;
                if (count > 100) {
                    return new NumberOfVariants(false, "Too many variants for string: " + s, 0);
                }
            }
            return new NumberOfVariants(true, null, count);
        }
        return new NumberOfVariants(false, "Wrong number format for string: " + s, 0);
    }

    public static NumberOfVariants getStringNumberOfVariants(String metadata) {
        if (StringUtils.isBlank(metadata)) {
            return ZERO_VARIANT;
        }

        String s = metadata.trim().toLowerCase();
        if (!StringUtils.startsWithAny(s, "[")) {
            return ONE_VARIANT;
        }
        if (s.startsWith("[")) {
            int count = 0;
            for (StringTokenizer st = new StringTokenizer(s, "[,] "); st.hasMoreTokens(); ) {
                String token = st.nextToken();
                count++;
            }
            return new NumberOfVariants(true, null, count);
        }
        return new NumberOfVariants(false, "Wrong number format for string: " + s, 0);
    }
}
