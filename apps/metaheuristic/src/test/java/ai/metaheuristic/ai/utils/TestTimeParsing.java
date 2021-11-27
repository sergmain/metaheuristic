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
package ai.metaheuristic.ai.utils;

import ai.metaheuristic.ai.commons.dispatcher_schedule.ExtendedTimePeriod;
import ai.metaheuristic.ai.commons.dispatcher_schedule.ExtendedTimePeriodUtils;
import ai.metaheuristic.ai.commons.dispatcher_schedule.DispatcherSchedule;
import ai.metaheuristic.ai.commons.dispatcher_schedule.TimePeriods;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class TestTimeParsing {

    @Data
    public static class SimpleYamlHolder {
        public String holder;
    }

    public static class SimpleYamlHolderUtils {
        private static final Yaml yaml;

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
            assertNotNull(is);
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

        DispatcherSchedule schedule = DispatcherSchedule.createDispatcherSchedule(holder.holder);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
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
            assertNotNull(is);
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

        DispatcherSchedule schedule = DispatcherSchedule.createDispatcherSchedule(holder.holder);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
            assertNotNull(is);
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

        DispatcherSchedule schedule = DispatcherSchedule.createDispatcherSchedule(holder.holder);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
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
            assertNotNull(is);
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

        DispatcherSchedule schedule = DispatcherSchedule.createDispatcherSchedule(holder.holder);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        assertTrue(schedule.isActive(LocalDateTime.parse( "14/01/2019 23:59", fmt)));

        DateTimeFormatter fmt1 = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        LocalDateTime time = LocalDateTime.parse("14/01/2019 23:59:29", fmt1);
        assertTrue(schedule.isActive(time));

        DateTimeFormatter fmt2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        time = LocalDateTime.parse("2019-05-10 23:59:19.138", fmt2);
        assertTrue(schedule.isActive(time));
    }

    @Test
    public void parseExtendedTimeYaml_weekendRestricted() throws IOException {
        SimpleYamlHolder holder;
        try (InputStream is = TestTimeParsing.class.getResourceAsStream("/yaml/time_periods/extended-time-weekend-restricted.yaml")) {
            assertNotNull(is);
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

        DispatcherSchedule schedule = DispatcherSchedule.createDispatcherSchedule(holder.holder);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS");
        assertFalse(schedule.isActive(LocalDateTime.parse( "27/04/2019 23:59:59.138", fmt)));
        assertTrue(schedule.isActive(LocalDateTime.parse( "27/04/2019 00:01:00.000", fmt)));

    }

    @Test
    public void testTimePeriodParsing() {
        String s = "0:00    - 8:45, 19:00 -   23:59";

        TimePeriods periods = TimePeriods.from(s);
        assertEquals(2, periods.periods.size());

        TimePeriods.TimePeriod tp1 = periods.periods.get(0);
        assertEquals(0, tp1.start.compareTo(TimePeriods.parseTime("0:00")));
        assertEquals(0, tp1.end.compareTo(TimePeriods.parseTime("8:45")));

        TimePeriods.TimePeriod tp2 = periods.periods.get(1);
        assertEquals(0, tp2.start.compareTo(TimePeriods.parseTime("19:00")));
        assertEquals(0, tp2.end.compareTo(TimePeriods.parseTime("23:59")));


        assertTrue(periods.isActive(TimePeriods.parseTime("0:0")));
        assertTrue(periods.isActive(TimePeriods.parseTime("00:00")));
        assertTrue(periods.isActive(TimePeriods.parseTime("08:45")));
        assertTrue(periods.isActive(TimePeriods.parseTime("19:00")));
        assertTrue(periods.isActive(TimePeriods.parseTime("23:59")));

        assertFalse(periods.isActive(TimePeriods.parseTime("12:00")));

    }

    @Test
    public void testTimePeriodAlwaysActive() {
        TimePeriods periods = TimePeriods.from(null);
        assertEquals(1, periods.periods.size());

        TimePeriods.TimePeriod tp1 = periods.periods.get(0);
        assertEquals(0, tp1.start.compareTo(TimePeriods.parseTime("0:00")));
        assertEquals(0, tp1.end.compareTo(TimePeriods.parseTime("23:59")));

        assertTrue(periods.isActive(TimePeriods.parseTime("0:0")));
        assertTrue(periods.isActive(TimePeriods.parseTime("00:00")));
        assertTrue(periods.isActive(TimePeriods.parseTime("08:45")));
        assertTrue(periods.isActive(TimePeriods.parseTime("19:00")));
        assertTrue(periods.isActive(TimePeriods.parseTime("23:59")));
    }

    @Test
    public void testTimeParsing() {
        LocalTime lt1 = TimePeriods.parseTime("0:00");
        LocalTime lt2 = TimePeriods.parseTime("8:45");
        LocalTime lt3 = TimePeriods.parseTime("19:00");
        LocalTime lt4 = TimePeriods.parseTime("23:59");

        LocalTime curr = TimePeriods.parseTime("22:00");
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

        LocalTime curr1 = TimePeriods.parseTime("23:59");
        System.out.println("" + curr1);

        System.out.println(curr1.compareTo(lt4)==0 );
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
