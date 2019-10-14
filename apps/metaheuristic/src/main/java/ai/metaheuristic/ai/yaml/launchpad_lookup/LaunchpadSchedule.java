/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.yaml.launchpad_lookup;

import ai.metaheuristic.ai.S;
import lombok.ToString;
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
@ToString(of = "asString")
public class LaunchpadSchedule {

    private final List<LocalDate> holidays = new ArrayList<>();
    private final List<LocalDate> exceptionWorkingDays = new ArrayList<>();
    private TimePeriods monday = TimePeriods.ALWAYS_ACTIVE;
    private TimePeriods tuesday = TimePeriods.ALWAYS_ACTIVE;
    private TimePeriods wednesday = TimePeriods.ALWAYS_ACTIVE;
    private TimePeriods thursday = TimePeriods.ALWAYS_ACTIVE;
    private TimePeriods friday = TimePeriods.ALWAYS_ACTIVE;
    private TimePeriods saturday = TimePeriods.ALWAYS_ACTIVE;
    private TimePeriods sunday = TimePeriods.ALWAYS_ACTIVE;
    public final ExtendedTimePeriod.SchedulePolicy policy;

/*
    Monday 	    Mon. 	Mo.
    Tuesday 	Tue. 	Tu.
    Wednesday 	Wed. 	We.
    Thursday 	Thu. 	Th.
    Friday 	    Fri. 	Fr.
    Saturday 	Sat. 	Sa.
    Sunday 	    Sun. 	Su
*/

    public final String asString;

    public LaunchpadSchedule(String cfg) {
        if (StringUtils.isBlank(cfg)) {
            this.policy = ExtendedTimePeriod.SchedulePolicy.normal;
            this.asString = "";
            return;
        }
        this.asString = cfg;
        final ExtendedTimePeriod config = ExtendedTimePeriodUtils.to(cfg);
        this.policy = config.policy;

        try {
            if (!S.b(config.workingDay)) {
                TimePeriods workingDay = TimePeriods.from(config.workingDay);
                monday = tuesday = wednesday = thursday = friday = workingDay;
            }
            if (!S.b(config.weekend)) {
                TimePeriods weekend = TimePeriods.from(config.weekend);
                saturday = sunday = weekend;
            }
            if (config.week!=null) {
                if (!S.b(config.week.mon)) {
                    monday = TimePeriods.from(config.week.mon);
                }
                if (!S.b(config.week.tue)) {
                    tuesday = TimePeriods.from(config.week.tue);
                }
                if (!S.b(config.week.wed)) {
                    wednesday = TimePeriods.from(config.week.wed);
                }
                if (!S.b(config.week.thu)) {
                    thursday = TimePeriods.from(config.week.thu);
                }
                if (!S.b(config.week.fri)) {
                    friday = TimePeriods.from(config.week.fri);
                }
                if (!S.b(config.week.sat)) {
                    saturday = TimePeriods.from(config.week.sat);
                }
                if (!S.b(config.week.sun)) {
                    sunday = TimePeriods.from(config.week.sun);
                }
            }

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
        final LocalDateTime curr = time.withSecondOfMinute(0).withMillisOfSecond(0);

        if (holidays.contains(curr.toLocalDate())) {
            return weekendIsActive(curr.toLocalTime());
        }
        if (exceptionWorkingDays.contains(curr.toLocalDate())) {
            return workingDaysActive(curr.toLocalTime());
        }
        TimePeriods periods;
        int i = curr.get(DateTimeFieldType.dayOfWeek());
        switch(i) {
            case 1:
                periods = monday;
                break;
            case 2:
                periods = tuesday;
                break;
            case 3:
                periods = wednesday;
                break;
            case 4:
                periods = thursday;
                break;
            case 5:
                periods = friday;
                break;
            case 6:
                periods = saturday;
                break;
            case 7:
                periods = sunday;
                break;
            default:
                throw new IllegalStateException("Wrong number of day of week " + i);
        }
        return periods.isActive(curr.toLocalTime());
    }

    private boolean workingDaysActive(LocalTime time) {
        return monday.isActive(time) || tuesday.isActive(time) || wednesday.isActive(time) ||
                thursday.isActive(time) || friday.isActive(time);
    }

    private boolean weekendIsActive(LocalTime time) {
        return saturday.isActive(time) || sunday.isActive(time);
    }

    public boolean isCurrentTimeInactive() {
        final LocalDateTime now = LocalDateTime.now();

        //noinspection RedundantIfStatement
        if (isActive(now)) {
            return false;
        } else {
            return true;
        }
    }

}
