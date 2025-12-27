/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ww2003.test_utils;

import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.WW2003Document;
import ai.metaheuristic.ww2003.document.comparator.DocumentComparator;
import ai.metaheuristic.ww2003.document.persistence.CommonWriter;
import ai.metaheuristic.ww2003.document.persistence.ww2003.WW2003WritersImpl;
import ai.metaheuristic.ww2003.utils.XmlUtils;

import java.io.StringWriter;

/**
 * @author Sergio Lissner
 * Date: 11/24/2022
 * Time: 2:31 PM
 */
public class W2003Utils {

    public static void validate(WW2003Document ww2003Document) {
        CDNode n = ww2003Document;
        boolean addRoot = false;
        CDNode nClone = n.clone();
        DocumentComparator.compare(n, nClone);

        StringWriter stringWriter = new StringWriter();
        WW2003WritersImpl.INSTANCE.write(CommonWriter.DEFAULT_CTX, nClone, stringWriter);
        String xml = stringWriter.toString();
        // "<?xml"
        XmlUtils.validate(xml, addRoot);
    }

}
