/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class ExecProcessService {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Result {
        public boolean isOk;
        public int exitCode;
        public String console;
    }

    private static class StreamHolder {
        private InputStream is;
    }

    // TODO 2018-10-30 need to implement for station side
/*
    private final LogDataRepository logDataRepository;

    public ProcessService(LogDataRepository logDataRepository) {
        this.logDataRepository = logDataRepository;
    }
*/

    public Result execCommand(List<String> cmd, File execDir, File consoleLogFile) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd);
        pb.directory(execDir);
        pb.redirectErrorStream(true);
        final Process process = pb.start();

        final StreamHolder streamHolder = new StreamHolder();
        int exitCode;
        try (final FileOutputStream fos = new FileOutputStream(consoleLogFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            final Thread reader = new Thread(() -> {
                try {
                    streamHolder.is = process.getInputStream();
                    int c;
                    while ((c = streamHolder.is.read()) != -1) {
                        bos.write(c);
                    }
                }
                catch (IOException e) {
                    log.error("Error collect data from output stream", e);
                }
            });
            reader.start();

            exitCode = process.waitFor();
            reader.join();
        }
        finally {
            try {
                if (streamHolder.is!=null) {
                    streamHolder.is.close();
                }
            }
            catch(Throwable th) {
                log.warn("Error with closing InputStream", th);
            }
        }

        log.info("Any errors of execution? {}", (exitCode == 0 ? "No" : "Yes"));
        log.debug("'\tcmd: {}", cmd);
        log.debug("'\texecDir: {}", execDir.getAbsolutePath());
        String console = readLastLines(500, consoleLogFile);
        log.debug("'\tconsole output:\n{}", console);

        return new Result(exitCode==0, exitCode, console);
    }

    private String readLastLines(int maxSize, File consoleLogFile) throws IOException {
        LinkedList<String> lines = new LinkedList<>();
        String inputLine;
        try(FileReader fileReader = new FileReader(consoleLogFile); BufferedReader in = new BufferedReader(fileReader) ) {
            while ((inputLine = in.readLine()) != null) {
                inputLine = inputLine.trim();
                if (lines.size()==maxSize) {
                    lines.removeFirst();
                }
                lines.add(inputLine);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }


}
