/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
package ai.metaheuristic.apps.simple_app;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootApplication
@Slf4j
public class SimpleApp implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(SimpleApp.class, args);
    }

    @Override
    public void run(String... args) throws IOException, InterruptedException {
        if (args.length==0) {
            System.out.println("Parameter file wasn't specified");
            System.exit(-1);
        }
        System.out.println("args = " + Arrays.toString(args));

        // sleep for testing timeoutBeforeTerminate
        System.out.println("Start timeout...");
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        System.out.println("Timeout ended.");

        if (args.length>1 ) {
            String message = "Just for test an error reporting. "+ CommonConsts.MULTI_LANG_STRING;
            log.error(message);
            throw new RuntimeException(message);
        }

        File yamlFile = new File(args[0]);
        String config = FileUtils.readFileToString(yamlFile, "utf-8");
        System.out.println("Yaml config file:\n"+config);

        TaskFileParamsYaml params = TaskFileParamsYamlUtils.BASE_YAML_UTILS.to(config);

        List<String> inputFiles = params.task.inputs
                .stream()
                .map(o->o.id.toString())
                .collect(Collectors.toList());
        System.out.println("input files: " + inputFiles);

        String outputFilename = params.task.outputs
                .stream()
                .filter(o->o.name.equals("processed-file"))
                .map(o->o.id.toString())
                .findFirst()
                .orElseThrow();

        System.out.println("output filename: " + outputFilename);

        File outputFile = Path.of(params.task.workingPath, ConstsApi.ARTIFACTS_DIR, outputFilename).toFile();
        FileUtils.write(outputFile, inputFiles.isEmpty() ? "No files were provided" : inputFiles.toString(), StandardCharsets.UTF_8);
    }

}