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

package ai.metaheuristic.ww2003.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class ListUtils {

    public static String listToStr (List<String> l){
        StringBuilder s = new StringBuilder();
        for (String s1 : l) {
            s.append(s1).append("\n");
        }
        return s.toString().strip();
    }

    public static String optimizeStrList(List<String> l){
        StringBuilder s = new StringBuilder();
        for (String s1 : l) {
            s.append(s1).append("\n");
        }
        String str = optimizeSir(s.toString());
        return str.strip();
    }

    @SuppressWarnings("Duplicates")
    public static String optimizeSir(String s){
        int ll = s.indexOf("{");
        int lll = s.lastIndexOf("}");
        String res="";
        if(ll!=-1 && lll!=-1) {
            String s1 = s.substring(0, ll).strip();
            String s2 = s.substring(ll, lll + 2).strip();
            String s3 = s.substring(lll + 2);
            if (s3.startsWith(" ") && !s3.endsWith(" ") && !s3.endsWith("\n") && !s3.startsWith("\n")) {
                s3 = s3;
            } else {
                s3 = s.substring(lll + 2).strip();
            }
            String c = "";

            StringTokenizer st;
            List<String> list = new ArrayList<>();
            st = new StringTokenizer(s2, "\n");
            while (st.hasMoreTokens()) {
                list.add(st.nextToken());
            }
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).contains("{")) {
                    list.remove(i);
                }
            }
            for (int i = 0; i < list.size(); i++) {
                if (list.size() > 1) {
                    c += list.get(i).strip() + " ";
                } else {
                    c = list.get(0).strip();
                }
            }
            res = s1 + " " + c + s3;
        }
        return res.strip();
    }
}
