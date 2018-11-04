/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

package aiai.ai.launchpad.experiment.dataset;

import aiai.apps.commons.utils.Checksum;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * User: Serg
 * Date: 03.08.2017
 * Time: 18:07
 */
public class DatasetChecksum {


    public static String getChecksumAsJson(File file) {

        try {

            String s;
            Checksum checksum = new Checksum();
            // C! don't use more that one type of checksum
/*
            try( InputStream inputStream = new FileInputStream(file)) {
                checksum.checksums.put(Type.MD5, Type.MD5.getChecksum(inputStream));
            }
*/
            try (InputStream inputStream = new FileInputStream(file)) {
                checksum.checksums.put(Checksum.Type.SHA256, Checksum.Type.SHA256.getChecksum(inputStream));
            }
            //noinspection UnnecessaryLocalVariable
            String jsonAsString = checksum.toJson();
            return jsonAsString;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error", e);
        }
    }

    public static void main(String[] args) {
        File file = new File("pom.xml");

        final String json = getChecksumAsJson(file);
        System.out.println("checksums: " + json);
        System.out.println("checksums len: " + json.length());

        Checksum checksum = Checksum.fromJson(json);

        System.out.println(checksum);
    }

}
