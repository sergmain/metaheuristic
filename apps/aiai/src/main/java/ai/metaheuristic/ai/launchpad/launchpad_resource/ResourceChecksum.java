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

package ai.metaheuristic.ai.launchpad.launchpad_resource;

import ai.metaheuristic.api.v1.EnumsApi;
import aiai.apps.commons.utils.Checksum;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * User: Serg
 * Date: 03.08.2017
 * Time: 18:07
 */
public class ResourceChecksum {


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
                checksum.checksums.put(EnumsApi.Type.SHA256, Checksum.getChecksum(EnumsApi.Type.SHA256, inputStream));
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
