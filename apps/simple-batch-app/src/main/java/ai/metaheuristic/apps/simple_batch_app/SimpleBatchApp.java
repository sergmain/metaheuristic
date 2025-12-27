/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
package ai.metaheuristic.apps.simple_batch_app;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.TaskFileParamsUtils;
import ai.metaheuristic.commons.yaml.batch.BatchItemMappingYaml;
import ai.metaheuristic.commons.yaml.batch.BatchItemMappingYamlUtils;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static ai.metaheuristic.commons.utils.TaskFileParamsUtils.getOutputVariableForType;

@SpringBootApplication
@Slf4j
public class SimpleBatchApp implements CommandLineRunner {

    public static void main(String[] args) {
        if (args.length==0) {
            System.out.println("Parameter file wasn't specified");
            System.exit(-1);
        }
        System.out.println("args = " + Arrays.toString(args));

        SpringApplication.run(SimpleBatchApp.class, args);
    }

    @Override
    public void run(String... args) throws IOException, InterruptedException {
        // sleep for testing timeoutBeforeTerminate
        System.out.println("Start timeout...");
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        System.out.println("Timeout ended.");

        if (args.length>1 ) {
            String message = "Just for test an error reporting. "+ CommonConsts.MULTI_LANG_STRING;
            log.error(message);
            throw new RuntimeException(message);
        }

        Path yamlFile = Path.of(args[0]);
        String config = Files.readString(yamlFile, StandardCharsets.UTF_8);
        System.out.println("Yaml config file:\n"+config);

        TaskFileParamsYaml params = TaskFileParamsYamlUtils.BASE_YAML_UTILS.to(config);

        if (params.task.inputs.size()!=1) {
            throw new RuntimeException("Too many input variables");
        }

        TaskFileParamsYaml.InputVariable arrayVariable = params.task.inputs.get(0);

        VariableArrayParamsYaml.Variable variable = TaskFileParamsUtils.getInputVariablesAsArray(params, arrayVariable).array.get(0);
        Path sourceFile = Path.of(params.task.workingPath, variable.dataType.toString(), variable.id);


        Map<String, List<TaskFileParamsYaml.OutputVariable>> processedVars = getOutputVariableForType(params, List.of("processed-file-type-1", "processed-file-type-2"));

        TaskFileParamsYaml.OutputVariable processedVar1 = processedVars.get("processed-file-type-1").get(0);
        String processedFilename1 = processedVar1.id;
        TaskFileParamsYaml.OutputVariable processedVar2 = processedVars.get("processed-file-type-2").get(0);
        String processedFilename2 = processedVar2.id;

        TaskFileParamsYaml.OutputVariable processingStatusVar = getOutputVariableForType(params, "processing-status-type");
        String processingStatusFilename = processingStatusVar.id;
        TaskFileParamsYaml.OutputVariable mappingVar = getOutputVariableForType(params, "mapping-type");
        String mappingFilename = mappingVar.id;

        System.out.println("processedFilename1: " + processedFilename1);
        System.out.println("processedFilename2: " + processedFilename2);
        System.out.println("processingStatusFilename: " + processingStatusFilename);
        System.out.println("mappingFilename: " + mappingFilename);

        Path artifactDir = Path.of(params.task.workingPath, ConstsApi.ARTIFACTS_DIR);

        Path processedFile1 = artifactDir.resolve(processedFilename1);
        Path processedFile2 = artifactDir.resolve(processedFilename2);
        Path processingStatusFile = artifactDir.resolve(processingStatusFilename);
        Path mappingFile = artifactDir.resolve(mappingFilename);

        Files.copy(sourceFile, processedFile1);
        Files.writeString(processingStatusFile, "File "+variable.filename +" was processed successfully", StandardCharsets.UTF_8);
        Files.writeString(processedFile2, "File processedFile2 was processed successfully", StandardCharsets.UTF_8);

        BatchItemMappingYaml bimy = new BatchItemMappingYaml();
        bimy.targetDir = S.b(variable.filename) ? "dir-" + variable.id : StrUtils.getName(variable.filename);
        bimy.filenames.put(processedVar1.id, variable.filename);
        bimy.filenames.put(processedVar2.id, variable.filename+"-comments.txt");
        bimy.filenames.put(processingStatusVar.id, S.b(variable.filename) ? "status.txt" : "status-for-" + StrUtils.getName(variable.filename)+".txt");

        String mapping = BatchItemMappingYamlUtils.BASE_YAML_UTILS.toString(bimy);
        Files.writeString(mappingFile, mapping, StandardCharsets.UTF_8);
    }

}