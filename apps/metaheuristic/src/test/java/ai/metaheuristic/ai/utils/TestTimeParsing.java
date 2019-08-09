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
package ai.metaheuristic.ai.utils;

import ai.metaheuristic.ai.yaml.launchpad_lookup.ExtendedTimePeriod;
import ai.metaheuristic.ai.yaml.launchpad_lookup.ExtendedTimePeriodUtils;
import ai.metaheuristic.ai.yaml.launchpad_lookup.LaunchpadSchedule;
import ai.metaheuristic.ai.yaml.launchpad_lookup.TimePeriods;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.Data;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.*;

public class TestTimeParsing {

    private static final DateTimeFormatter FORMATTER = DateTimeFormat.forPattern("HH:mm");

    @Data
    public static class SimpleYamlHolder {
        public String holder;
    }

    public static class SimpleYamlHolderUtils {
        private static Yaml yaml;

        static {
            yaml = YamlUtils.init(SimpleYamlHolder.class);
        }

        public static String toString(SimpleYamlHolder config) {
            return YamlUtils.toString(config, yaml);
        }

        public static SimpleYamlHolder to(String s) {
            return (SimpleYamlHolder) YamlUtils.to(s, yaml);
        }

        public static SimpleYamlHolder to(InputStream is) {
            return (SimpleYamlHolder) YamlUtils.to(is, yaml);
        }

        public static SimpleYamlHolder to(File file) {
            return (SimpleYamlHolder) YamlUtils.to(file, yaml);
        }
    }

    @Test
    public void parseExtendedTimeYaml() throws IOException, ParseException {


        SimpleYamlHolder holder;
        try (InputStream is = TestTimeParsing.class.getResourceAsStream("/yaml/time_periods/extended-time-period.yaml")) {
            holder = SimpleYamlHolderUtils.to(is);
        }
        assertNotNull(holder);
        assertNotNull(holder.holder);
        ExtendedTimePeriod period = ExtendedTimePeriodUtils.to(holder.holder);

        assertEquals("0:00-8:45, 19:00-23:59", period.workingDay);
        assertEquals("0:00-23:59", period.weekend);
        assertEquals("dd/MM/yyyy", period.dayMask);
        assertEquals("15/01/2019,16/01/2019", period.holiday);
        assertEquals("19/01/2019", period.exceptionWorkingDay);

        SimpleDateFormat sdf = new SimpleDateFormat(period.dayMask);
        Date date = sdf.parse(period.exceptionWorkingDay);

        Calendar c = Calendar.getInstance();
        c.setTime(date);
        assertEquals(19, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(1, c.get(Calendar.MONTH)+1);
        assertEquals(2019, c.get(Calendar.YEAR));
        assertEquals(Calendar.SATURDAY, c.get(Calendar.DAY_OF_WEEK));

        LaunchpadSchedule schedule = new LaunchpadSchedule(holder.holder);
        DateTimeFormatter fmt = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm");
        assertFalse(schedule.isActive(LocalDateTime.parse( "14/01/2019 13:05", fmt)));
        assertTrue(schedule.isActive(LocalDateTime.parse( "15/01/2019 13:05", fmt)));
        assertTrue(schedule.isActive(LocalDateTime.parse( "16/01/2019 13:05", fmt)));
        assertFalse(schedule.isActive(LocalDateTime.parse( "17/01/2019 13:05", fmt)));
        assertFalse(schedule.isActive(LocalDateTime.parse( "18/01/2019 13:05", fmt)));
        assertFalse(schedule.isActive(LocalDateTime.parse( "19/01/2019 13:05", fmt)));
        assertTrue(schedule.isActive(LocalDateTime.parse( "20/01/2019 13:05", fmt)));
    }

    @Test
    public void parseExtendedTimeYamlWithWeek() throws IOException, ParseException {


        SimpleYamlHolder holder;
        try (InputStream is = TestTimeParsing.class.getResourceAsStream("/yaml/time_periods/extended-time-period-with-weeks.yaml")) {
            holder = SimpleYamlHolderUtils.to(is);
        }
        assertNotNull(holder);
        assertNotNull(holder.holder);
        ExtendedTimePeriod period = ExtendedTimePeriodUtils.to(holder.holder);

        assertNull(period.workingDay);
        assertNull(period.weekend);
        assertNotNull(period.week);

        assertEquals("dd/MM/yyyy", period.dayMask);
        assertEquals("15/01/2019,16/01/2019", period.holiday);
        assertEquals("19/01/2019", period.exceptionWorkingDay);

        SimpleDateFormat sdf = new SimpleDateFormat(period.dayMask);
        Date date = sdf.parse(period.exceptionWorkingDay);

        Calendar c = Calendar.getInstance();
        c.setTime(date);
        assertEquals(19, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(1, c.get(Calendar.MONTH)+1);
        assertEquals(2019, c.get(Calendar.YEAR));
        assertEquals(Calendar.SATURDAY, c.get(Calendar.DAY_OF_WEEK));

        LaunchpadSchedule schedule = new LaunchpadSchedule(holder.holder);
        DateTimeFormatter fmt = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm");

        assertFalse(schedule.isActive(LocalDateTime.parse( "05/08/2019 13:05", fmt)));
        assertTrue(schedule.isActive(LocalDateTime.parse( "06/08/2019 13:05", fmt)));
        assertTrue(schedule.isActive(LocalDateTime.parse( "07/08/2019 13:05", fmt)));
        assertFalse(schedule.isActive(LocalDateTime.parse( "08/08/2019 13:05", fmt)));
        assertTrue(schedule.isActive(LocalDateTime.parse( "09/08/2019 13:05", fmt)));
        assertFalse(schedule.isActive(LocalDateTime.parse( "10/08/2019 13:05", fmt)));
        assertTrue(schedule.isActive(LocalDateTime.parse( "11/08/2019 13:05", fmt)));

        assertTrue(schedule.isActive(LocalDateTime.parse( "20/01/2019 13:05", fmt)));
    }

    @Test
    public void parseExtendedTimeYaml_short() throws IOException {


        SimpleYamlHolder holder;
        try (InputStream is = TestTimeParsing.class.getResourceAsStream("/yaml/time_periods//extended-time-period-short.yaml")) {
            holder = SimpleYamlHolderUtils.to(is);
        }
        assertNotNull(holder);
        assertNotNull(holder.holder);
        ExtendedTimePeriod period = ExtendedTimePeriodUtils.to(holder.holder);

        assertEquals("0:00-8:45, 19:00-23:59", period.workingDay);
        assertEquals("0:00-23:59", period.weekend);
        assertNull(period.dayMask);
        assertNull(period.holiday);
        assertNull(period.exceptionWorkingDay);

        LaunchpadSchedule schedule = new LaunchpadSchedule(holder.holder);
        DateTimeFormatter fmt = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm");
        assertFalse(schedule.isActive(LocalDateTime.parse( "14/01/2019 13:05", fmt)));
        assertFalse(schedule.isActive(LocalDateTime.parse( "15/01/2019 13:05", fmt)));
        assertFalse(schedule.isActive(LocalDateTime.parse( "16/01/2019 13:05", fmt)));
        assertFalse(schedule.isActive(LocalDateTime.parse( "17/01/2019 13:05", fmt)));
        assertFalse(schedule.isActive(LocalDateTime.parse( "18/01/2019 13:05", fmt)));
        assertTrue(schedule.isActive(LocalDateTime.parse( "19/01/2019 13:05", fmt)));
        assertTrue(schedule.isActive(LocalDateTime.parse( "20/01/2019 13:05", fmt)));
    }

    @Test
    public void parseExtendedTimeYaml_alwaysPermitted() throws IOException {
        SimpleYamlHolder holder;
        try (InputStream is = TestTimeParsing.class.getResourceAsStream("/yaml/time_periods//extended-time-period-always-permitted.yaml")) {
            holder = SimpleYamlHolderUtils.to(is);
        }
        assertNotNull(holder);
        assertNotNull(holder.holder);
        ExtendedTimePeriod period = ExtendedTimePeriodUtils.to(holder.holder);

        assertEquals("0:00-23:59", period.workingDay);
        assertEquals("0:00-23:59", period.weekend);
        assertNull(period.dayMask);
        assertNull(period.holiday);
        assertNull(period.exceptionWorkingDay);

        LaunchpadSchedule schedule = new LaunchpadSchedule(holder.holder);
        DateTimeFormatter fmt = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm");
        assertTrue(schedule.isActive(LocalDateTime.parse( "14/01/2019 23:59", fmt)));

        DateTimeFormatter fmt1 = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
        LocalDateTime time = LocalDateTime.parse("14/01/2019 23:59:29", fmt1);
        assertTrue(schedule.isActive(time));

        DateTimeFormatter fmt2 = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        time = LocalDateTime.parse("2019-05-10 23:59:19", fmt2);
        assertTrue(schedule.isActive(time));
    }

    @Test
    public void parseExtendedTimeYaml_weekendRestricted() throws IOException {
        SimpleYamlHolder holder;
        try (InputStream is = TestTimeParsing.class.getResourceAsStream("/yaml/time_periods//extended-time-weekend-restricted.yaml")) {
            holder = SimpleYamlHolderUtils.to(is);
        }
        assertNotNull(holder);
        assertNotNull(holder.holder);
        ExtendedTimePeriod period = ExtendedTimePeriodUtils.to(holder.holder);

        assertEquals("0:00-23:59", period.workingDay);
//        assertNull(period.weekend);
        assertNull(period.dayMask);
        assertNull(period.holiday);
        assertNull(period.exceptionWorkingDay);

        LaunchpadSchedule schedule = new LaunchpadSchedule(holder.holder);
        DateTimeFormatter fmt = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
        assertFalse(schedule.isActive(LocalDateTime.parse( "27/04/2019 23:59:59", fmt)));
        assertFalse(schedule.isActive(LocalDateTime.parse( "27/04/2019 00:01:00", fmt)));

    }

    @Test
    public void testTimePeriodParsing() {
        String s = "0:00    - 8:45, 19:00 -   23:59";

        TimePeriods periods = TimePeriods.from(s);
        assertEquals(2, periods.periods.size());

        TimePeriods.TimePeriod tp1 = periods.periods.get(0);
        assertTrue(tp1.start.isEqual(LocalTime.parse("0:00", FORMATTER)));
        assertTrue(tp1.end.isEqual(LocalTime.parse("8:45", FORMATTER)));

        TimePeriods.TimePeriod tp2 = periods.periods.get(1);
        assertTrue(tp2.start.isEqual(LocalTime.parse("19:00", FORMATTER)));
        assertTrue(tp2.end.isEqual(LocalTime.parse("23:59", FORMATTER)));


        assertTrue(periods.isActive(LocalTime.parse("0:0", FORMATTER)));
        assertTrue(periods.isActive(LocalTime.parse("00:00", FORMATTER)));
        assertTrue(periods.isActive(LocalTime.parse("08:45", FORMATTER)));
        assertTrue(periods.isActive(LocalTime.parse("19:00", FORMATTER)));
        assertTrue(periods.isActive(LocalTime.parse("23:59", FORMATTER)));

        assertFalse(periods.isActive(LocalTime.parse("12:00", FORMATTER)));

    }

    @Test
    public void testTimePeriodAlwaysActive() {
        TimePeriods periods = TimePeriods.from(null);
        assertEquals(1, periods.periods.size());

        TimePeriods.TimePeriod tp1 = periods.periods.get(0);
        assertTrue(tp1.start.isEqual(LocalTime.parse("0:00", FORMATTER)));
        assertTrue(tp1.end.isEqual(LocalTime.parse("23:59", FORMATTER)));

        assertTrue(periods.isActive(LocalTime.parse("0:0", FORMATTER)));
        assertTrue(periods.isActive(LocalTime.parse("00:00", FORMATTER)));
        assertTrue(periods.isActive(LocalTime.parse("08:45", FORMATTER)));
        assertTrue(periods.isActive(LocalTime.parse("19:00", FORMATTER)));
        assertTrue(periods.isActive(LocalTime.parse("23:59", FORMATTER)));
    }

    @Test
    public void testTimeParsing() {
        LocalTime lt1 = LocalTime.parse("0:00", FORMATTER);
        LocalTime lt2 = LocalTime.parse("8:45", FORMATTER);
        LocalTime lt3 = LocalTime.parse("19:00", FORMATTER);
        LocalTime lt4 = LocalTime.parse("23:59", FORMATTER);

        LocalTime curr = LocalTime.parse("22:00", FORMATTER);
        System.out.println("" + curr);

        System.out.println(curr.isAfter(lt1) );
        System.out.println(curr.isAfter(lt2) );
        System.out.println(curr.isAfter(lt3) );
        System.out.println(curr.isAfter(lt4) );
        System.out.println();

        System.out.println(curr.isBefore(lt1) );
        System.out.println(curr.isBefore(lt2) );
        System.out.println(curr.isBefore(lt3) );
        System.out.println(curr.isBefore(lt4) );
        System.out.println();

        LocalTime curr1 = LocalTime.parse("23:59", FORMATTER);
        System.out.println("" + curr1);

        System.out.println(curr1.isEqual(lt4) );
        System.out.println();

        System.out.println(curr1.isAfter(lt1) );
        System.out.println(curr1.isAfter(lt2) );
        System.out.println(curr1.isAfter(lt3) );
        System.out.println(curr1.isAfter(lt4) );
        System.out.println();

        System.out.println(curr1.isBefore(lt1) );
        System.out.println(curr1.isBefore(lt2) );
        System.out.println(curr1.isBefore(lt3) );
        System.out.println(curr1.isBefore(lt4) );


    }
}
