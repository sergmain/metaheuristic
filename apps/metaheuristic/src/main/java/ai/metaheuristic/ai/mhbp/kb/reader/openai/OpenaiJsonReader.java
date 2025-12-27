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

package ai.metaheuristic.ai.mhbp.kb.reader.openai;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.mhbp.questions.QuestionData;
import ai.metaheuristic.ai.mhbp.yaml.kb.KbParams;
import ai.metaheuristic.commons.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.SneakyThrows;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.mhbp.questions.QuestionData.Chapters;

/**
 * @author Sergio Lissner
 * Date: 4/19/2023
 * Time: 11:17 PM
 */
public class OpenaiJsonReader {


    public static final Chapters NOT_YET = new Chapters(Enums.KbSourceInitStatus.not_yet);
    public static final String SAMPLES_JSONL = "samples_jsonl";
    public static final String MATCH = "evals.elsuite.basic.match:Match";

    @SneakyThrows
    public static Chapters read(long kbId, Path repoDir, @Nullable KbParams.Git git) {
        if (git==null) {
            return NOT_YET;
        }
        Chapters chapters = new Chapters(kbId);
        for (KbParams.KbPath kbPath : git.kbPaths) {
            Path evals = repoDir.resolve(kbPath.evals);

            final List<String> paths = new ArrayList<>();
            Files.walkFileTree(evals, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String yaml = Files.readString(file);
                    String jsonlPath = parseAndGetJsonlPath(yaml);
                    if (jsonlPath!=null) {
                        paths.add(jsonlPath);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            Path data = repoDir.resolve(kbPath.data);
            for (String path : paths) {
                QuestionData.ChapterWithPrompts chapter = new QuestionData.ChapterWithPrompts(path, new ArrayList<>(1000));
                chapters.chapters.add(chapter);
                Path jsonlPath = data.resolve(path);
                if (Files.notExists(jsonlPath)) {
                    System.out.println("\tNot exists: " + jsonlPath);
                    continue;
                }
                System.out.println("Exists: " + jsonlPath);
                List<String> jsonls = Files.readAllLines(jsonlPath);
                for (String jsonl : jsonls) {
                    OpenaiInput input = toOpenaiInput(jsonl);
                    StringBuilder sb = new StringBuilder();
                    boolean isFirst = true;
                    for (OpenaiInput.Input in : input.input) {
                        if (isFirst) {
                            isFirst = false;
                        }
                        else {
                            sb.append('\n');
                        }
                        sb.append(in.content);
                    }
                    chapter.list().add(new QuestionData.QuestionWithAnswerToAsk(sb.toString(), input.getIdeal()));
                }
            }

        }
        chapters.initStatus = Enums.KbSourceInitStatus.ready;
        return chapters;
    }

    @Nullable
    public static String parseAndGetJsonlPath(String s) {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(true);
        loaderOptions.setMaxAliasesForCollections(10);
        loaderOptions.setAllowRecursiveKeys(false);

        Constructor c = new Constructor(Map.class, loaderOptions);
        Yaml yaml = new Yaml(c);

        Map map = (Map) yaml.load(s);

        final Map.Entry match = isMatch(map);
//        System.out.println(match);
        final String jsonlPath = getJsonlPath(match);
        return jsonlPath;
    }

    @Nullable
    public static Map.Entry<Object, Object> isMatch(Map<Object, Object> map) {
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map mmap) {
                if (MATCH.equals(mmap.get("class"))) {
                    return entry;
                }
            }
        }
        return null;
    }

    @Nullable
    public static String getJsonlPath(@Nullable Map.Entry match) {
        if (match==null) {
            return null;
        }
        if (!(match.getValue() instanceof Map mmap)) {
            return null;
        }

        Object o = mmap.get("args");

        if (!(o instanceof Map argsMap)) {
            return null;
        }

        final Object o1 = argsMap.get(SAMPLES_JSONL);
        return o1 instanceof String ? (String)o1 : null;
    }

    public static OpenaiInput toOpenaiInput(String json) throws JsonProcessingException {
        try {
            return JsonUtils.getMapper().readValue(json, OpenaiInput.class);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
