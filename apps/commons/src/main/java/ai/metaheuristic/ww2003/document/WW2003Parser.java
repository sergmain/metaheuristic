/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ww2003.document;

import ai.metaheuristic.ww2003.document.compress.Compressor;

import java.io.InputStream;

public class WW2003Parser {

    public static WW2003Document parse(InputStream inputStream) {
        return parse(inputStream, false);
    }


    public static WW2003Document parse(InputStream inputStream, boolean cardOnly) {
        WW2003Document document = DocumentParser.parse(inputStream, cardOnly);

        Compressor.compressRunTags(document);

        return document;
    }

}