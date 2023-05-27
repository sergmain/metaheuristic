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

import ai.metaheuristic.ww2003.Consts;
import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.Enums;
import ai.metaheuristic.ww2003.document.tags.xml.Para;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static ai.metaheuristic.ww2003.Enums.Align.*;

/**
 * @author Serge
 * Date: 6/28/2021
 * Time: 1:56 PM
 */
public class ParaUtils {

    public static boolean onlyDashes(CDNode node) {
        String s = node.getTextSmart().strip();
        return StringUtils.containsOnly(s, '-') && s.length()>10;
    }

    public record TargetNodes(CDNode placeForInsert, CDNode lastParagraph) {}

    public static Para[] split(String text) {
        List<Para> paras = new ArrayList<>(100);
        for (String s : StringUtils.splitPreserveAllTokens(text, '\n')) {
            if (s.length()==1 && s.charAt(0)=='\n') {
                continue;
            }
            char ch;
            if (s.length() == 0) {
                paras.add(Para.rt(""));
            }
            else {
                ch = s.charAt(0);
                ai.metaheuristic.ww2003.Enums.Align align = none;
                int off = 2;
                boolean comment = false;
                switch(ch) {
                    case '<' -> align = left;
                    case '^' -> align = center;
                    case '>' -> align = right;
                    case '-' -> //noinspection DataFlowIssue
                            align = none;
                    case '=' -> align = both;
                    case '#' -> comment = true;
                    default -> {
                        off = 0;
                    }
                }
                if (comment) {
                    continue;
                }
                String actual;
                if (s.length()==1) {
                    actual = "";
                }
                else {
                    actual = s.substring(off);
                }
                final Para para = Para.rt(actual);
                para.setAlign(align);
                paras.add(para);
            }
        }
        return paras.toArray(Para[]::new);
    }

    public static boolean checkEmptyParagraphsAfter(CDNode node) {
        return countEmptyParagraphsAfter(node) >= Consts.EMPTY_PARAGRAPH_COUNT;
    }

    public static boolean checkEmptyParagraphs(CDNode node, Enums.BypassDirection direction) {
        return countEmptyParagraphs(node, direction) >= Consts.EMPTY_PARAGRAPH_COUNT;
    }

    public static boolean checkEmptyParagraphsAfter(int count, CDNode node) {
        return countEmptyParagraphsAfter(node) >= count;
    }

    public static boolean checkEmptyParagraphs(int count, CDNode node, Enums.BypassDirection direction) {
        return countEmptyParagraphs(node, direction) >= count;
    }

    public static int countEmptyParagraphsAfter(CDNode n) {
        return countEmptyParagraphs(n, ai.metaheuristic.ww2003.document.Enums.BypassDirection.FORWARD);
    }

    public static int countEmptyParagraphs(CDNode n, Enums.BypassDirection direction) {
        Function<CDNode, CDNode> walkerFunc = direction == ai.metaheuristic.ww2003.document.Enums.BypassDirection.FORWARD
                ? (node)->node.hasNext() ? node.getNext() : null
                : (node)->node.hasPrev() ? node.getPrev() : null;
        return countEmptyParagraphs(n, walkerFunc);
    }

    public static int countEmptyParagraphs(CDNode n, Function<CDNode, CDNode> walkerFunc) {
        int count =0;
        CDNode node = walkerFunc.apply(n);
        while (node!=null && node.instanceOfPara() && node.isBlank()) {
            count++;
            node = walkerFunc.apply(node);
        }
        return count;
    }
}
