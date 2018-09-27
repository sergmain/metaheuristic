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

import aiai.ai.yaml.env.TimePeriods;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestTimeParsing {

    private static final DateTimeFormatter FORMATTER = DateTimeFormat.forPattern("HH:mm");

    @Test
    public void testTimePeriodParsing() {
        String s = "0:00 - 8:45, 19:00 - 23:59";

        TimePeriods periods = TimePeriods.from(s);
        assertEquals(2, periods.periods.size());

        TimePeriods.TimePeriod tp1 = periods.periods.get(0);
        assertTrue(tp1.start.isEqual(LocalTime.parse("0:00", FORMATTER)));
        assertTrue(tp1.end.isEqual(LocalTime.parse("8:45", FORMATTER)));

        TimePeriods.TimePeriod tp2 = periods.periods.get(1);
        assertTrue(tp2.start.isEqual(LocalTime.parse("19:00", FORMATTER)));
        assertTrue(tp2.end.isEqual(LocalTime.parse("23:59", FORMATTER)));
    }

    @Test
    public void testTimeParsing() {
        String s = "0:00 - 8:45, 19:00 - 23:59";

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
