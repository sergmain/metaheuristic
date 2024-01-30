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

package ai.metaheuristic.ai.internal_function.reduce_variables;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Serge
 * Date: 4/21/2022
 * Time: 5:16 PM
 */
@Disabled
class AttentionUtils {

    private static final String EPOCH = "epochs";
    private static final Pattern EPOCH_PATTERN = Pattern.compile(".*,\"epoch\":\"(?<epochs>\\d+?)\",");

    private static final String JSON_FOR_CHECK1 = """
            {"seed":"42","isClusterSize1":"true","epoch":"1000","isBinaryDrawWithFrequency":"true","isDistribOfFreqFull":"true","RNN":"LSTM","optimizer":"adamax","isClusterCount1":"false","isBinaryClusters1":"false","isMatrixOfWinning":"false","activation":"relu","batchSize":"20","timeSteps":"10"}
            """;
    private static final String JSON_FOR_CHECK2 = """
            {"seed":"42","isClusterSize1":"true","epoch":"2000","isBinaryDrawWithFrequency":"true","isDistribOfFreqFull":"true","RNN":"LSTM","optimizer":"adamax","isClusterCount1":"false","isBinaryClusters1":"false","isMatrixOfWinning":"false","activation":"relu","batchSize":"20","timeSteps":"10"}
            """;
    public static final String ATTENTION_1_TXT = "attention-1.txt";

    @BeforeAll
    static void before() {
        final Matcher m1 = EPOCH_PATTERN.matcher(JSON_FOR_CHECK1);
        assertTrue(m1.find());
        assertEquals(1000, getEpoch(m1));
        final Matcher m2 = EPOCH_PATTERN.matcher(JSON_FOR_CHECK2);
        assertTrue(m2.find());
        assertEquals(2000, getEpoch(m2));

        assertEquals(JSON_FOR_CHECK2, replaceEpochWith(2000, m1, JSON_FOR_CHECK1));

    }

    public static final List<Integer> EPOCHS = List.of(1000, 5000, 10000);


    @Test
    public void addEpochs() throws IOException {
        Path output = Path.of("result", ATTENTION_1_TXT+".1");
        List<String> newLines = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("result", ATTENTION_1_TXT))) {
            final Matcher m1 = EPOCH_PATTERN.matcher(line);
            assertTrue(m1.find());
            int currEpoch = getEpoch(m1);

            EPOCHS.forEach(i -> {
                if (i < currEpoch) {
                    return;
                }
                String newLine = replaceEpochWith(i, m1, line);
                newLines.add(newLine);
            });
        }
        FileUtils.writeLines(output.toFile(), newLines, "\n");
    }

    public static int getEpoch(Matcher m) {
        String epochStr = m.group(EPOCH);
        int epoch = Integer.parseInt(epochStr);
        return epoch;
    }

    public static String replaceEpochWith(int epoch, Matcher m, String s) {
        int start = m.start(EPOCH);
        int end = m.end(EPOCH);
        String newLine = s.substring(0, start) + epoch + s.substring(end);
        return newLine;
    }
}
