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

package aiai.ai.core;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * User: Serg
 * Date: 03.08.2017
 * Time: 21:38
 */
public class TestCreateDatasetPath {


    @Test
    public void testCreatePath() {
        Assert.assertEquals("000004", String.format("%06d", 4) );
    }
}
