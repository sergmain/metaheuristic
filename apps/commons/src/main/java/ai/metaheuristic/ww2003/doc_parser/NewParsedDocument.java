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

package ai.metaheuristic.ww2003.doc_parser;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
public class NewParsedDocument {

    public String infoBankCode;

    public Map<String, List<String>> fields;

    public String filename;

    public NewParsedDocument(Map<String, List<String>> fields) {
        this.fields = fields;
    }

    public NewParsedDocument(Map<String, List<String>> fields, String filename) {
        this.fields = fields;
        this.filename = filename;
    }

    public List<String> getField(String field) {
        return fields.getOrDefault(field, Collections.emptyList());
    }

    public String getFieldWithComma(String field) {
        return getFieldWithDelimiter(field, ", ");
    }

    public String getFieldWithSpace(String field) {
        return getFieldWithDelimiter(field, " ");
    }

    public String getFieldWithCaretka(String field) {
        return getFieldWithDelimiter(field, "\n");
    }

    public String getFieldWithoutDelimiter(String field) {
        return getFieldWithDelimiter(field, "");
    }

    private String getFieldWithDelimiter(String field, String delimiter) {
        boolean isFirst = true;
        StringBuilder sb = new StringBuilder();
        for (String f : getField(field)) {
            if (isFirst) {
                isFirst = false;
            }
            else {
                sb.append(delimiter);
            }
            sb.append(f);
        }
        return sb.toString();
    }

}
