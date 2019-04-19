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

package aiai.ai.yaml.launchpad_lookup;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.*;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Slf4j
public class LaunchpadSchedule {

    private final List<LocalDate> holidays = new ArrayList<>();
    private final List<LocalDate> exceptionWorkingDays = new ArrayList<>();
    private TimePeriods workingDay = TimePeriods.ALWAYS_ACTIVE;
    private TimePeriods weekend = TimePeriods.ALWAYS_ACTIVE;

    public final String asString;

    public LaunchpadSchedule(String cfg) {
        if (StringUtils.isBlank(cfg)) {
            this.asString = "";
            return;
        }
        this.asString = cfg;
        final ExtendedTimePeriod config = ExtendedTimePeriodUtils.to(cfg);

        try {
            this.workingDay = TimePeriods.from(config.workingDay);
            this.weekend = TimePeriods.from(config.weekend);

            if (config.dayMask!=null) {
                DateTimeFormatter fmt = DateTimeFormat.forPattern(config.dayMask);

                toLocalDate(fmt, config.holiday, holidays);
                toLocalDate(fmt, config.exceptionWorkingDay, exceptionWorkingDays);
            }
        } catch (ParseException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error", e);
        }
    }

    private void toLocalDate(DateTimeFormatter fmt, String datesAsStr, List<LocalDate> dates) throws ParseException {
        if (StringUtils.isBlank(datesAsStr)) {
            return;
        }
        for (java.util.StringTokenizer st = new java.util.StringTokenizer(datesAsStr, ","); st.hasMoreTokens(); ) {
            String token = st.nextToken().trim();
            if (StringUtils.isBlank(token)) {
                continue;
            }
            dates.add(LocalDate.parse( token, fmt));
        }
    }

    private void toCalendars(String dayMask, String dates, List<Calendar> calendars) throws ParseException {
        for (java.util.StringTokenizer st = new java.util.StringTokenizer(dates, ","); st.hasMoreTokens(); ) {
            String token = st.nextToken().trim();
            if (StringUtils.isBlank(token)) {
                continue;
            }

            SimpleDateFormat sdf = new SimpleDateFormat(dayMask);
            Date date = sdf.parse(token);

            Calendar c = Calendar.getInstance();
            c.setTime(date);

            calendars.add(c);
        }
    }

    public boolean isActive(final LocalDateTime time) {
        int secs = time.getSecondOfMinute();
        final LocalDateTime curr = time.minusSeconds(secs);

        if (holidays.contains(curr.toLocalDate())) {
            return weekend.isActive(curr.toLocalTime());
        }
        if (exceptionWorkingDays.contains(curr.toLocalDate())) {
            return workingDay.isActive(curr.toLocalTime());
        }
        TimePeriods periods;
        int i = curr.get(DateTimeFieldType.dayOfWeek());
        if (i >= 1 && i <= 5) {
            periods = workingDay;
        } else if (i == 6 || i == 7) {
            periods = weekend;
        } else {
            throw new IllegalStateException("Wrong day of week " + i);
        }
        return periods.isActive(curr.toLocalTime());
    }

    public boolean isCurrentTimeInactive() {
        return !isActive(LocalDateTime.now());
    }

}
