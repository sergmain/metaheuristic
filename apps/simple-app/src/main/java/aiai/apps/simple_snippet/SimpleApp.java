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
package aiai.apps.simple_snippet;

import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class SimpleApp implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(SimpleApp.class, args);
    }

    private Map<String, Object> cfg;

    @Override
    public void run(String... args) throws IOException {
        if (args.length==0) {
            System.out.println("Parameter file wasn't specified");
            System.exit(-1);
        }
        File yamlFile = new File(args[0]);
        String config = FileUtils.readFileToString(yamlFile, "utf-8");
        System.out.println("Yaml config file:\n"+config);

        Yaml yaml = new Yaml();
        cfg = yaml.load(config);

        String inputFile = getInputFile();
        System.out.println("input file: " + inputFile);
        String outputFile = getOutputFile();
        System.out.println("output file: " + outputFile);

        FileUtils.copyFile(
                new File("DATA" + File.separatorChar + inputFile),
                new File("artifacts" + File.separatorChar + outputFile)
        );
    }

    public String getOutputFile() {
        return ""+cfg.get("outputResourceCode");
    }

    public String getInputFile() {
        Map<String, List<String>> inputResourceCodes = (Map)cfg.get("inputResourceCodes");
        Collection<List<String>> values = inputResourceCodes.values();
        if (values.isEmpty()) {
            throw new IllegalStateException("inputResourceCodes is empty");
        }
        List<String> list = values.iterator().next();
        if (list.isEmpty()) {
            throw new IllegalStateException("inputResourceCodes/list  is empty");
        }
        return list.get(0);
    }
}