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

import ai.metaheuristic.ww2003.document.tags.EndOfSection;
import ai.metaheuristic.ww2003.document.tags.Indentation;
import ai.metaheuristic.ww2003.document.tags.xml.Text;
import ai.metaheuristic.ww2003.utils.TableUtils;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static ai.metaheuristic.ww2003.Enums.Align.center;
import static ai.metaheuristic.ww2003.Enums.Align.right;

/**
 * @author Serge
 * Date: 5/28/2021
 * Time: 11:41 PM
 */
public class CDNodeUtils {

    private static final AtomicInteger nodeId = new AtomicInteger(0);

    public static int getNewNodeId() {
        return nodeId.incrementAndGet();
    }

    public static List<Composite> getNodesFromStartToEnd(CDNode startNode, CDNode endNode) {
        final List<CDNode> cdNodes = new ArrayList<>();
        CDNode cdNode = startNode;
        while (cdNode!=null && cdNode != endNode) {
            cdNodes.add(cdNode);
            cdNode = cdNode.hasNext() ? cdNode.getNext() : null;
        }
        if (cdNode==endNode) {
            cdNodes.add(cdNode);
        }
        return (List)cdNodes;
    }


    public static void copyAlignment(CDNode src, CDNode trg) {
        if (src instanceof Composite cSrc && trg instanceof Composite cTrg) {
            cTrg.setAlign(cSrc.getAlign());
        }
    }

    public static void copyIndentation(CDNode src, CDNode trg){
        if (src instanceof Indentation indSrc && trg instanceof Indentation indTrg) {
            indTrg.setIndent(indSrc.getIndent());
        }
    }

    @Nullable
    public static Integer getRealIndent(Composite composite) {
        return getRealIndent(composite, null);
    }

    @Nullable
    public static Integer getRealIndent(Composite composite, @Nullable CDNode prevNode) {
        if (composite instanceof Indentation indentation) {
            Integer firstLine = indentation.getIndent();
            if (firstLine == null && !TableUtils.isNodeInsideTable(composite)) {
                if (composite.getAlign() == center || composite.getAlign() == right) {
                    firstLine = 0;
                }
            }
            return firstLine;
        }
        return null;
    }

    public static boolean isNodeOrOuterNodeCentered(CDNode node) {
        if (WW2003DocumentUtils.isNodeCentered(node)) {
            return true;
        } else {
            CDNode nodeParent = node;
            while (nodeParent.hasParent()) {
                nodeParent = nodeParent.getParent();
                if (WW2003DocumentUtils.isNodeCentered(nodeParent)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<CDNode> stripBlankOrEndOfSectionAtEnd(List<CDNode> nodes) {
        if (isEndsWithEmpty(nodes)) {
            nodes.remove(nodes.size() - 1);
        }
        if (isEndsWithEmpty(nodes)) {
            nodes.remove(nodes.size() - 1);
        }
        return nodes;
    }

    private static boolean isEndsWithEmpty(List<CDNode> nodes) {
        if (nodes.isEmpty()) {
            return false;
        }
        CDNode n = nodes.get(nodes.size()-1);
        return n instanceof EndOfSection || n.isBlank();
    }

    public static String getTextSmart(CDNode cdNode) {
        final StringBuilder sb = new StringBuilder();

        AtomicReference<Character> lastChar = new AtomicReference<>(null);
        cdNode.asStream(Text.class).map(Text::getText).forEach(s->{
            if (s.length()==0) {
                return;
            }
            final char ch = s.charAt(0);
            boolean needSeparator = needSeparator(lastChar.get(), ch);
            if (needSeparator) {
                sb.append(' ');
            }
            lastChar.set(s.charAt(s.length()-1));

            sb.append(s);
        });
        return sb.toString();
    }

    public static boolean needSeparator(@Nullable Character lastChar, char firstChar) {
        if (lastChar==null) {
            return false;
        }
        if (isQuotedChars(lastChar)) {
            return false;
        }
        if (Character.isWhitespace(lastChar) || Character.isWhitespace(firstChar)) {
            return false;
        }
        if (!Character.isWhitespace(lastChar) && !Character.isWhitespace(firstChar) && !isSentenceSeparator(lastChar) && !isSentenceSeparator(firstChar)) {
            return true;
        }
        if (isSentenceSeparator(lastChar) && isSentenceSeparator(firstChar)) {
            return false;
        }
        if (!Character.isWhitespace(lastChar) && !Character.isWhitespace(firstChar) && !isSentenceSeparator(lastChar)) {
            return false;
        }
        return true;
    }

    private static final char[] CHARS_MAIN = new char[] {'.', ',', '[', ']', '(', ')', ';', ':', '!'};
    private static final char[] CHARS_QUOTE = new char[] {'\'', '"'};
    private static boolean isQuotedChars(char ch) {
        if (isInArray(ch, CHARS_QUOTE)) {
            return true;
        }
        return false;
    }

    private static boolean isSentenceSeparator(char ch) {
        if (Character.isWhitespace(ch)) {
            return true;
        }
        if (isInArray(ch, CHARS_MAIN)) {
            return true;
        }
        return false;
    }

    private static boolean isInArray(char ch, char[] charsMain) {
        for (char aChar : charsMain) {
            if (ch==aChar) {
                return true;
            }
        }
        return false;
    }
}
