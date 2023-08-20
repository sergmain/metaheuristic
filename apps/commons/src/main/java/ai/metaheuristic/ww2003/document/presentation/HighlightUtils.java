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

package ai.metaheuristic.ww2003.document.presentation;

import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.Composite;
import ai.metaheuristic.ww2003.document.tags.Property;
import ai.metaheuristic.ww2003.document.tags.xml.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class HighlightUtils {

    public static void noHighlight(CDNode cdNode) {
    }

    public static void highlightEdition(CDNode cdNode) {
        highlightNode(cdNode, HighlightColor.EDITION);
    }

    public static void highlightExpired(CDNode cdNode) {
        highlightNode(cdNode, HighlightColor.EXPIRED);
    }

    public static void highlightExclusion(CDNode cdNode) {
        highlightNode(cdNode, HighlightColor.WORDS_EXCLUSION);
    }

    public static void highlightNonProcessedLink(CDNode cdNode) {
        highlightNode(cdNode, HighlightColor.NON_PROCESSED);
    }

    public static void highlightNode(CDNode cdNode, HighlightColor color) {
        if (cdNode instanceof Run run) {
            highlightRun(run, color);
        } else {
            cdNode.asStream(Run.class).forEach(run -> highlightRun(run, color));
        }
    }

    public static void highlightRun(Run run, HighlightColor color) {
        String val = run.findProperty(RProp.class, Highlight.class)
                .flatMap(propElement -> propElement.findAttributeByName("val").map(attr -> attr.value)).orElse(null);

        HighlightColor highlightColor = HighlightColor.asHighlightColor(val);
        if (highlightColor==HighlightColor.ERROR || highlightColor==HighlightColor.EDITION) {
            return;
        }

        final Highlight newHighlight = new Highlight(Attr.get("w", "val", color.getColorName()));
        run.addPropertyElement(RProp.class, newHighlight);
    }

    public static void highlightWords(List<? extends CDNode> parents, List<Pair<Integer, Integer>> indicesList) {
        highlightWords(parents, indicesList, HighlightColor.EDITION);
    }

    public static void highlightWords(List<? extends CDNode> parents, List<Pair<Integer, Integer>> indicesList, HighlightColor color) {
        indicesList.forEach(indices -> {
            List<Run> runs = parents.stream()
                    .flatMap(parent -> parent.asStream(Run.class))
                    .collect(Collectors.toList());
            highlightWords(runs, indices.getLeft(), indices.getRight(), color);
        });
    }

    public static void highlightWords(List<Run> runs, Integer hiStart, Integer hiEnd, HighlightColor color) {
        AtomicInteger counter = new AtomicInteger(0);
        List<CDNode> parentList = new ArrayList<>();
        int spaceOffset = -1;
        for (Run run : runs) {
            Composite outerNode = null;
            if (outerNode != null && !parentList.contains(outerNode)) {
                parentList.add(outerNode);
                ++spaceOffset;
            }
            int hiStartCorrected = hiStart - spaceOffset;
            int hiEndCorrected = hiEnd - spaceOffset;
            int runLength = run.getText().length();
            int runStart = counter.get();
            int runEnd = counter.get() + runLength;

            if (runEnd <= hiStartCorrected || hiEndCorrected <= runStart) {
                counter.addAndGet(runLength);
                continue;
            }

            if (hiStartCorrected <= runStart && runEnd <= hiEndCorrected) {
                highlightNode(run, color);
                counter.addAndGet(runLength);
                continue;
            }
            Run correctedRun = splitStartRunByIndices(run, runStart, hiStartCorrected);
            Run finishedRun = splitEndRunByIndices(correctedRun, runEnd, hiEndCorrected);
            highlightNode(finishedRun, color);
            counter.addAndGet(runLength);
        }
    }

    private static Run splitStartRunByIndices(Run run, int runStart, Integer hiStart) {
        if (runStart >= hiStart) {
            return run;
        }
        String text = run.getText();
        int cutLength = hiStart - runStart;
        List<Property> properties = run.streamProperties().collect(Collectors.toList());
        Run firstRun = new Run(new Text(text.substring(0, cutLength)));
        properties.forEach(firstRun::setProperty);
        firstRun.setAlign(run.getAlign());

        Run secondRun = new Run(new Text(text.substring(cutLength)));
        properties.forEach(secondRun::setProperty);
        secondRun.setAlign(run.getAlign());

        run.getParent().replace(List.of(run), List.of(firstRun, secondRun));
        return secondRun;
    }

    private static Run splitEndRunByIndices(Run run, int runEnd, int hiEnd) {
        if (runEnd <= hiEnd) {
            return run;
        }
        String text = run.getText();
        int cutLength = runEnd - hiEnd;
        String textFromRight = StringUtils.right(text, cutLength);
        String textFromLeft = text.substring(0, text.length() - textFromRight.length());
        List<Property> properties = run.streamProperties().collect(Collectors.toList());
        Run firstRun = new Run(new Text(textFromLeft));
        properties.forEach(firstRun::setProperty);
        firstRun.setAlign(run.getAlign());

        Run secondRun = new Run(new Text(textFromRight));
        properties.forEach(secondRun::setProperty);
        secondRun.setAlign(run.getAlign());

        run.getParent().replace(List.of(run), List.of(firstRun, secondRun));
        return firstRun;
    }

}
