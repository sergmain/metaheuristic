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
package aiai.ai.yaml.env;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

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

    private static final DateTimeFormatter FORMATTER = DateTimeFormat.forPattern("HH:mm");

    public static final TimePeriods ALWAYS_ACTIVE = new TimePeriods(new TimePeriod( LocalTime.parse("0:00", FORMATTER),  LocalTime.parse("23:59", FORMATTER)));

    public TimePeriods(TimePeriod period) {
        periods.add( period );
    }

    private static TimePeriod asTimePeriod(String s) {
        final int idx = s.indexOf('-');
        if (idx ==-1) {
            throw new IllegalArgumentException("Wrong format of string for parsing: " + s+". Must be in format [HH:mm - HH:mm] (without brackets)");
        }
        //noinspection UnnecessaryLocalVariable
        TimePeriod period = new TimePeriod( LocalTime.parse(s.substring(0, idx).trim(), FORMATTER),  LocalTime.parse(s.substring(idx+1).trim(), FORMATTER));
        return period;
    }

    public static TimePeriods from(String s) {
        if (StringUtils.isBlank(s)) {
            return ALWAYS_ACTIVE;
        }

        TimePeriods periods = new TimePeriods();
        for (StringTokenizer st = new StringTokenizer(s, ","); st.hasMoreTokens(); ) {
            String token = st.nextToken().trim();
            periods.periods.add( asTimePeriod(token) );
        }
        return periods;
    }


    public boolean isActive(LocalTime curr, TimePeriod period) {
        return curr.isEqual(period.start) || curr.isEqual(period.end) || ( curr.isAfter(period.start) && curr.isBefore(period.end));
    }

    public boolean isActive(LocalTime curr) {
        for (TimePeriod period : periods) {
            if (isActive(curr, period)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCurrentTimeActive() {
        return isActive(LocalTime.fromMillisOfDay(System.currentTimeMillis()));
    }
}
