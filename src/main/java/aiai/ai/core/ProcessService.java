/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.core;

import aiai.ai.launchpad.beans.LogData;
import aiai.ai.launchpad.repositories.LogDataRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
@Slf4j
public class ProcessService {

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

    private final LogDataRepository logDataRepository;

    public ProcessService(LogDataRepository logDataRepository) {
        this.logDataRepository = logDataRepository;
    }

    public Result execCommand(LogData.Type type, Long refId, List<String> cmd, File execDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd);
        pb.directory(execDir);
        pb.redirectErrorStream(true);
        final Process process = pb.start();

        final StringBuilder out = new StringBuilder();
        final StreamHolder streamHolder = new StreamHolder();
        int exitCode;
        try {
            final Thread reader = new Thread(() -> {
                try {
                    streamHolder.is = process.getInputStream();
                    int c;
                    while ((c = streamHolder.is.read()) != -1) {
                        out.append((char) c);
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
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
        log.debug("Console output: {}",out);
        LogData logData = new LogData();
        logData.setRefId(refId);
        logData.setType(type);
        final String console = out.toString();
        logData.setLogData(console);
        logDataRepository.save(logData);

        return new Result(exitCode==0, exitCode, console);
    }


}
