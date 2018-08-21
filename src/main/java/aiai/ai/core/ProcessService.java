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

import aiai.ai.beans.LogData;
import aiai.ai.repositories.LogDataRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class ProcessService {

    private final LogDataRepository logDataRepository;

    public ProcessService(LogDataRepository logDataRepository) {
        this.logDataRepository = logDataRepository;
    }

    public boolean execCommand(LogData.Type type, Long refId, List<String> cmd, File execDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd);
        pb.directory(execDir);
        pb.redirectErrorStream(true);
        final Process process = pb.start();

        final StringBuilder out = new StringBuilder();
        final Thread reader = new Thread(() -> {
            try {
                final InputStream is = process.getInputStream();
                int c;
                while ((c = is.read()) != -1) {
                    out.append((char) c);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        reader.start();

        final int exitCode = process.waitFor();
        reader.join();

        System.out.println("Any errors of execution? " + (exitCode == 0 ? "No" : "Yes"));
        System.out.println(out);
        LogData logData = new LogData();
        logData.setRefId(refId);
        logData.setType(type);
        logData.setLogData(out.toString());
        logDataRepository.save(logData);

        return exitCode==0;
    }


}
