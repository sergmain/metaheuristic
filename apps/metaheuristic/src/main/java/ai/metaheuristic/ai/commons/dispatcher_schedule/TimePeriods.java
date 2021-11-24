/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
package ai.metaheuristic.ai.commons.dispatcher_schedule;

import ai.metaheuristic.commons.S;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Data
@NoArgsConstructor
public class TimePeriods {

    @Data
    @AllArgsConstructor
    public static class TimePeriod {
        public final LocalTime start;
        public final LocalTime end;
    }

    public final List<TimePeriod> periods = new ArrayList<>();
    public String asString = ""; // for ALWAYS_ACTIVE

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static final TimePeriods ALWAYS_ACTIVE = new TimePeriods(
            new TimePeriod( parseTime("0:0"),  parseTime("23:59")));

    private TimePeriods(TimePeriod period) {
        periods.add( period );
    }

    private static TimePeriod asTimePeriod(String s) {
        final int idx = s.indexOf('-');
        if (idx ==-1) {
            throw new IllegalArgumentException("Wrong format of string for parsing: " + s+". Must be in format [HH:mm - HH:mm] (without brackets)");
        }
        TimePeriod period = new TimePeriod(parseTime(s.substring(0, idx).trim()), parseTime(s.substring(idx+1).trim()));
        return period;
    }

    public static LocalTime parseTime(String timeAsStr) {
        return LocalTime.parse(fix(timeAsStr), TIME_FORMATTER);
    }

    private static String fix(String t) {
        if (S.b(t) || t.length()<3) {
            throw new IllegalStateException("Wrong time string: " + t);
        }
        String s = null;
        if (t.charAt(1) == ':') {
            s = "0" + t;
        }
        if (t.charAt(t.length()-2)==':') {
            s = s + '0';
        }
        return s==null ? t : s;
    }

    public static TimePeriods from(@Nullable String s) {
        if (StringUtils.isBlank(s)) {
            return ALWAYS_ACTIVE;
        }

        TimePeriods periods = new TimePeriods();
        periods.asString = s;
        for (StringTokenizer st = new StringTokenizer(s, ","); st.hasMoreTokens(); ) {
            String token = st.nextToken().trim();
            periods.periods.add( asTimePeriod(token) );
        }
        return periods;
    }

    private static boolean isActive(LocalTime curr, TimePeriods.TimePeriod period) {
        return curr.compareTo(period.start)==0 || curr.compareTo(period.end)==0 || ( curr.isAfter(period.start) && curr.isBefore(period.end));
    }

    public boolean isActive(LocalTime curr) {
        for (TimePeriods.TimePeriod period : periods) {
            if (isActive(curr, period)) {
                return true;
            }
        }
        return false;
    }
}
