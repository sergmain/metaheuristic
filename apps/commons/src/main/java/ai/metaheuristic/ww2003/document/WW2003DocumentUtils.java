/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ww2003.Consts;
import ai.metaheuristic.ww2003.document.persistence.CommonWriter;
import ai.metaheuristic.ww2003.document.persistence.ww2003.WW2003WritersImpl;
import lombok.SneakyThrows;
import javax.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class WW2003DocumentUtils {

    public static void removeEmptyTailNodes(List<CDNode> nodes) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            CDNode node = nodes.get(i);
            if (node.getText().isBlank()) {
                nodes.remove(i);
            } else {
                break;
            }
        }
    }

    public static boolean isNodeCentered(@Nullable CDNode node) {
        return node instanceof Composite composite && composite.getAlign() == ai.metaheuristic.ww2003.Enums.Align.center;
    }

    public static boolean isNodeRightAligned(@Nullable CDNode node) {
        return node instanceof Composite composite && composite.getAlign() == ai.metaheuristic.ww2003.Enums.Align.right;
    }

    public static CDNode skipEmptyNodes(CDNode node) {
        CDNode nextNode = node;
        if (nextNode.isNotBlank()) {
            return nextNode;
        }
        while (nextNode.hasNext()) {
            if ((nextNode = nextNode.getNext()).isNotBlank()) {
                break;
            }
        }
        return nextNode;
    }

    @Nullable
    public static CDNode getNextNotBlankNode(CDNode node) {
        CDNode nextNode = node.hasNext() ? node.getNext() : null;
        while (nextNode!=null) {
            if (nextNode.isNotBlank()) {
                return nextNode;
            }
            nextNode = nextNode.hasNext() ? nextNode.getNext() : null;
        }
        return null;
    }

    public static CDNode skipEmptyAndRightJustifiedNodes(CDNode node) {
        for (; node.hasNext(); node = node.getNext()) {
            if (node.isNotBlank() && !WW2003DocumentUtils.isNodeRightAligned(node)) {
                return node;
            }
        }
        return node;
    }

    public static CDNode skipEmptyAndCenteredNodes(CDNode node) {
        return skipEmptyAndCenteredNodes(node, false);
    }

    public static CDNode skipEmptyAndCenteredNodes(CDNode node, boolean skipCurrent) {
        CDNode currNode = (skipCurrent && node.hasNext()) ? node.getNext() : node;
        for (; currNode.hasNext(); currNode = currNode.getNext()) {
            if (!currNode.isBlank() && !WW2003DocumentUtils.isNodeCentered(currNode)) {
                return currNode;
            }
        }
        return currNode;
    }

    public static CDNode skipEmptyAndCenteredMarks(CDNode node) {
        for (; node.hasNext(); node = node.getNext()) {
            if (!node.isBlank() && !WW2003DocumentUtils.isNodeCentered(node) || !node.instanceOfPara()) {
                return node;
            }
        }
        return node;
    }

    static Optional<LocalDate> parseDateOptional(String str) {
        return Optional.of(LocalDate.parse(str, Consts.DATE_TIME_FORMATTER));
    }

    public static void writeWW2003Document(Path dumpFile, WW2003Document ww2003Document) {
        writeWW2003Document(dumpFile, ww2003Document, WW2003WritersImpl.INSTANCE);
    }

    @SneakyThrows
    public static void writeWW2003Document(Path dumpFile, WW2003Document ww2003Document, CommonWriter commonWriter) {
        try (OutputStream os = Files.newOutputStream(dumpFile);
            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw, 0xffff)) {
            commonWriter.write(CommonWriter.DEFAULT_CTX, ww2003Document, writer);
            writer.flush();
        }
    }


}