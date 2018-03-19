/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

package aiai.ai.launchpad.dataset;

import aiai.ai.beans.LogData;
import aiai.ai.repositories.*;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:21
 */
@Controller
@RequestMapping("/launchpad")
public class DatasetsController {

    public static final String ASSEMBLY_DATASET_YAML = "assembly-dataset.yaml";
    public static final String DEFINITIONS_DIR = "definitions";

    @Value("${aiai.table.rows.limit}")
    private int limit;

    @Value("${aiai.launchpad.dir}")
    private String launchpadDirAsString;

    private DatasetsRepository repository;
    private DatasetGroupsRepository groupsRepository;
    private DatasetColumnRepository columnRepository;
    private DatasetPathRepository pathRepository;
    private LogDataRepository logDataRepository;

    public DatasetsController(DatasetsRepository repository, DatasetGroupsRepository groupsRepository, DatasetColumnRepository columnRepository, DatasetPathRepository pathRepository, LogDataRepository logDataRepository) {
        this.repository = repository;
        this.groupsRepository = groupsRepository;
        this.columnRepository = columnRepository;
        this.pathRepository = pathRepository;
        this.logDataRepository = logDataRepository;
    }

    private static File toFile(String launchpadDirAsString) {
        if (launchpadDirAsString.charAt(0) == '.' && (launchpadDirAsString.charAt(1) == '\\' || launchpadDirAsString.charAt(1) == '/')) {
            return new File(launchpadDirAsString.substring(2));
        }
        return new File(launchpadDirAsString);
    }

    @GetMapping("/datasets")
    public String init(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/launchpad/datasets";
    }

    // for AJAX
    @PostMapping("/datasets-part")
    public String getDatasets(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/launchpad/datasets :: table";
    }

    @GetMapping(value = "/dataset-add")
    public String add(Model model) {
        model.addAttribute("dataset", new Dataset());
        return "/launchpad/dataset-form";
    }

    @GetMapping(value = "/dataset-edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("dataset", repository.findById(id));
        return "/launchpad/dataset-form";
    }

    @GetMapping(value = "/dataset-definition/{id}")
    public String toDatasetDefinition(@PathVariable(name = "id") Long datasetId, Model model) {
        final Optional<Dataset> datasetOptional = repository.findById(datasetId);
        if (!datasetOptional.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        final Dataset dataset = datasetOptional.get();

        final String path = String.format("<Launchpad directory>%c%s%c%03d", File.separatorChar, DEFINITIONS_DIR, File.separatorChar, dataset.getId());
        final File launchpadDir = toFile(launchpadDirAsString);
        final File datasetDir = new File(launchpadDir, path);

//        final DatasetDefinition definition = new DatasetDefinition(dataset, launchpadDirAsString, datasetDir.getPath());
        final DatasetDefinition definition = new DatasetDefinition(dataset, launchpadDirAsString, path);
        definition.paths = pathRepository.findByDataset_OrderByPathNumber(dataset);


        // fix conditions for UI
        final int groupSize = dataset.getDatasetGroups().size();
        for (int i = 0; i < groupSize; i++) {
            DatasetGroup group = dataset.getDatasetGroups().get(i);
            group.setAddColumn(true);
/*
    // TODO что это за кусок кода?
            group.setAddColumn(false);
            if (i > 0) {
                DatasetGroup groupPrev = dataset.getDatasetGroups().get(i - 1);
                if (groupPrev.getDatasetColumns().isEmpty()) {
                    break;
                }
            }

            if (groupSize == 1) {
                group.setAddColumn(true);
                continue;
            }
            if (i == groupSize - 1) {
                group.setAddColumn(true);
                continue;
            }
            DatasetGroup groupNext = dataset.getDatasetGroups().get(i + 1);
            if (groupNext.getDatasetColumns().isEmpty()) {
                group.setAddColumn(true);
                //noinspection UnnecessaryContinue
                continue;
            }
*/
        }

        // ugly but it works
        boolean isAllEmpty = true;
        for (DatasetGroup group : dataset.getDatasetGroups()) {
            if (!group.getDatasetColumns().isEmpty()) {
                isAllEmpty = false;
                break;
            }
        }

        // don't invert the condition, because ...
        // TODO 2018-03-17 I forgot why
        //noinspection StatementWithEmptyBody
        if (isAllEmpty) {
            // nothing to do with this
        } else {
            // last actual column in groups. there isn't any non-empty group after this one
            for (int i = 0; i < dataset.getDatasetGroups().size(); i++) {

                // case when last group isn't empty
                if (i + 1 == dataset.getDatasetGroups().size()) {
                    final List<DatasetColumn> columns = dataset.getDatasetGroups().get(i).getDatasetColumns();
                    columns.get(columns.size() - 1).setLastColumn(true);
                    break;
                }
                // case when there are some empty groups
                if (i < dataset.getDatasetGroups().size() - 1 && dataset.getDatasetGroups().get(i + 1).getDatasetColumns().size() == 0) {
                    final List<DatasetColumn> columns = dataset.getDatasetGroups().get(i).getDatasetColumns();
                    if (columns.isEmpty()) {
                        continue;
                    }
                    columns.get(columns.size() - 1).setLastColumn(true);
                    break;
                }
            }
        }

        model.addAttribute("result", definition);
        return "/launchpad/dataset-definition";
    }

    @GetMapping(value = "/dataset-column-add/{id}")
    public String addColumn(@PathVariable(name = "id") Long datasetGroupId, Model model) {
        final Optional<DatasetGroup> value = groupsRepository.findById(datasetGroupId);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        final DatasetColumn column = new DatasetColumn();
        column.setDatasetGroup(group);
        model.addAttribute("column", column);
        return "/launchpad/dataset-column-form";
    }

    @PostMapping(value = "/dataset-column-add-commit")
    public String addNewColumnCommit(DatasetColumn column) {
        final Optional<DatasetGroup> value = groupsRepository.findById(column.getDatasetGroup().getId());
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();

        column.setDatasetGroup(group);
        columnRepository.save(column);

        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @GetMapping(value = "/dataset-column-edit/{id}")
    public String editDatasetColumn(@PathVariable Long id, Model model) {
        final Optional<DatasetColumn> optionalColumn = columnRepository.findById(id);
        if (!optionalColumn.isPresent()) {
            return "redirect:/launchpad/datasets";
        }

        model.addAttribute("column", optionalColumn.get());
        return "/launchpad/dataset-column-form";
    }

    @PostMapping("/dataset-column-form-commit")
    public String datasetColumnFormCommit(DatasetColumn column) {
        columnRepository.save(column);
        return "redirect:/launchpad/dataset-definition/" + column.getDatasetGroup().getDataset().getId();
    }

    @GetMapping("/dataset-column-delete/{id}")
    public String deleteColumn(@PathVariable Long id, Model model) {
        final Optional<DatasetColumn> value = columnRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        model.addAttribute("column", value.get());
        return "/launchpad/dataset-column-delete";
    }

    @PostMapping("/dataset-column-delete-commit")
    public String deleteColumnCommit(Long id) {
        final Optional<DatasetColumn> value = columnRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetColumn column = value.get();
        final long datasetId = column.getDatasetGroup().getDataset().getId();
        columnRepository.deleteById(id);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-column-move-prev-group/{id}")
    public String moveColumnToPrevGroup(@PathVariable Long id) {
        final Optional<DatasetColumn> value = columnRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetColumn column = value.get();
        final Dataset dataset = column.getDatasetGroup().getDataset();
        List<DatasetGroup> groups = dataset.getDatasetGroups();
        if (groups.size() < 2) {
            return "redirect:/launchpad/datasets";
        }

        DatasetGroup prevGroup = null;
        for (DatasetGroup group : groups) {
            if (column.getDatasetGroup().getId() == group.getId()) {
                if (prevGroup == null) {
                    return "redirect:/launchpad/datasets";
                }
                column.setDatasetGroup(prevGroup);
                break;
            }
            prevGroup = group;
        }

        columnRepository.save(column);
        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @GetMapping(value = "/dataset-column-move-next-group/{id}")
    public String moveColumnToNextGroup(@PathVariable Long id) {
        final Optional<DatasetColumn> value = columnRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetColumn column = value.get();
        final Dataset dataset = column.getDatasetGroup().getDataset();
        List<DatasetGroup> groups = dataset.getDatasetGroups();
        if (groups.size() < 2) {
            return "redirect:/launchpad/datasets";
        }

        for (int i = 0; i < groups.size() - 1; i++) {
            DatasetGroup group = groups.get(i);
            if (column.getDatasetGroup().getId().equals(group.getId())) {
                DatasetGroup nextGroup = groups.get(i + 1);
                column.setDatasetGroup(nextGroup);
                break;
            }
        }

        columnRepository.save(column);
        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @GetMapping(value = "/dataset-delete-group/{id}")
    public String deleteGroup(@PathVariable Long id) {
        final Optional<DatasetGroup> value = groupsRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        long datasetId = group.getDataset().getId();
        if (!group.getDatasetColumns().isEmpty()) {
            return "redirect:/launchpad/datasets";
        }

        groupsRepository.delete(group);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-group-add-new/{id}")
    public String addNewGroup(@PathVariable(name = "id") Long datasetId) {
        final Optional<Dataset> value = repository.findById(datasetId);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = value.get();

        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(datasetId);
        int groupNumber;
        //noinspection ConstantConditions
        groupNumber = groups.isEmpty() ? 1 : groups.stream().mapToInt(DatasetGroup::getGroupNumber).max().getAsInt() + 1;

        final DatasetGroup group = new DatasetGroup(groupNumber);
        group.setFeature(false);
        group.setDataset(dataset);

        dataset.getDatasetGroups().add(group);

        repository.save(dataset);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-group-add-new-feature/{id}")
    public String addNewFeatureGroup(@PathVariable(name = "id") Long datasetId) {
        final Optional<Dataset> value = repository.findById(datasetId);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = value.get();

        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(datasetId);
        int groupNumber;
        //noinspection ConstantConditions
        groupNumber = groups.isEmpty() ? 1 : groups.stream().mapToInt(DatasetGroup::getGroupNumber).max().getAsInt() + 1;

        final DatasetGroup group = new DatasetGroup(groupNumber);
        group.setFeature(true);
        group.setDataset(dataset);

        dataset.getDatasetGroups().add(group);

        repository.save(dataset);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @PostMapping("/dataset-group-cmd-commit")
    public String groupCommandFormCommit(Long id, String command) {
        final Optional<DatasetGroup> value = groupsRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        group.setCommand(command);
        groupsRepository.save(group);
        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @PostMapping(value = "/dataset-group-id-group-commit")
    public String setIdGrouppForGroup(Long id, @RequestParam(name = "id_group", required = false, defaultValue = "false") boolean isIdGroup) {
        final Optional<DatasetGroup> value = groupsRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        group.setIdGroup(isIdGroup);
        groupsRepository.save(group);

        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @PostMapping(value = "/dataset-group-label-commit")
    public String setLabelForGroup(Long id, boolean label) {
        final Optional<DatasetGroup> value = groupsRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        group.setLabel(label);
        groupsRepository.save(group);

        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @PostMapping(value = "/dataset-is-editable-commit")
    public String setHeaderForDataset(Long id, boolean editable) {
        final Optional<Dataset> value = repository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = value.get();
        dataset.setEditable(editable);
        repository.save(dataset);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @PostMapping(value = "/dataset-cmd-assemble-commit")
    public String setHeaderForDataset(Long id, @RequestParam(name = "command_assemble") String assemblingCommand) {
        final Optional<Dataset> value = repository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = value.get();
        dataset.setAssemblingCommand(assemblingCommand);
        repository.save(dataset);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @PostMapping(value = "/dataset-run-assembling-commit")
    public String runAssemblingOfDataset(Long id) throws IOException {
        final Optional<Dataset> value = repository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = value.get();

        File yaml = createAssemblingYaml(dataset);
        runAssembling(dataset, yaml);
        linkeDatasetFile(dataset);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    private void linkeDatasetFile(Dataset dataset) {
        final File launchpadDir = toFile(launchpadDirAsString);
        final String path = String.format("%s%c%03d", DEFINITIONS_DIR, File.separatorChar, dataset.getId());

        final File datasetDefDir = new File(launchpadDir, path);
        if (!datasetDefDir.exists()) {
            return;
        }

        String datasetFilename = String.format("%s%cdataset%cdataset.txt", path, File.separatorChar, File.separatorChar );

        File datasetFile = new File(launchpadDir, datasetFilename);
        if (!datasetFile.exists()) {
            return;
        }
        dataset.setDatasetFile(datasetFilename);
        repository.save(dataset);
    }

    private static String output(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.getProperty("line.separator"));
            }
        }
        return sb.toString();
    }


    private void runAssembling(Dataset dataset, File yaml) {

        // https://examples.javacodegeeks.com/core-java/lang/processbuilder/java-lang-processbuilder-example/
        //
        // java -jar bin\app-assembly-dataset-1.0-SNAPSHOT.jar 6
        try {
            List<String> cmd = Arrays.stream(dataset.getAssemblingCommand().split("\\s+")).collect(Collectors.toList());
            cmd.add(yaml.getPath());

            ProcessBuilder pb = new ProcessBuilder();
            pb.command(cmd);
            File directory = toFile(launchpadDirAsString);
            pb.directory(directory.getCanonicalFile());
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

            System.out.println("any errors of assembling? " + (exitCode == 0 ? "No" : "Yes"));
            System.out.println(out);
            LogData logData = new LogData();
            logData.setRefId(dataset.getId());
            logData.setType(LogData.Type.ASSEMBLY);
            logData.setLogData(out.toString());
            logDataRepository.save(logData);

        }
        catch (Exception err) {
            err.printStackTrace();
        }
    }

    private File createAssemblingYaml(Dataset dataset) throws IOException {
        final File launchpadDir = toFile(launchpadDirAsString);

        final String path = String.format("%s%c%03d", DEFINITIONS_DIR, File.separatorChar, dataset.getId());
        final File datasetDefDir = new File(launchpadDir, path);
        if (!datasetDefDir.exists()) {
            boolean status = datasetDefDir.mkdirs();
            if (!status) {
                throw new IllegalStateException("Error create directory: " + datasetDefDir.getAbsolutePath());
            }
        }

        File yamlFile = new File(datasetDefDir, ASSEMBLY_DATASET_YAML);
        File yamlFileBak = new File(datasetDefDir, ASSEMBLY_DATASET_YAML+".bak");
        yamlFileBak.delete();
        if (yamlFile.exists()) {
            yamlFile.renameTo(yamlFileBak);
        }


        File datasetDir = new File(datasetDefDir, "dataset");
        if (!datasetDir.isDirectory()) {
            throw new IllegalStateException("Not a directory: " + datasetDir.getCanonicalPath());
        }

        File datatsetFile = new File(datasetDir, "dataset.csv");
        File datatsetFileBak = new File(datasetDir, "dataset.csv.bak");

        datatsetFileBak.delete();
        if (datatsetFile.exists()) {
            datatsetFile.renameTo(datatsetFileBak);
        }

        List<DatasetPath> paths = pathRepository.findByDataset_OrderByPathNumber(dataset);

/*
        dataset:
            raws:
                - raws\file_01.txt
                - raws\file_02.txt
                - raws\file_03.txt
            output:
                dataset\dataset.txt
*/

        String s = "";
        s += "dataset:\n    raws:\n";
        for (DatasetPath datasetPath : paths) {
            s += "        - "+datasetPath.getPath()+'\n';
        }
        s += ("    output:\n        "+ String.format("%s%cdataset%cdataset.txt\n", path, File.separatorChar, File.separatorChar ));

        try  {
            FileUtils.write(yamlFile, s, "utf-8", false);
        } catch (IOException e) {
            throw new RuntimeException("error", e);
        }

        return new File(path, ASSEMBLY_DATASET_YAML);
    }


    private static final Set<String> exts;

    static {
        exts = new HashSet<>();
        Collections.addAll(exts, ".json", ".csv", ".txt", ".xml");
    }

    private static boolean checkExtension(String filename) {
        int idx;
        if ((idx=filename.lastIndexOf('.'))==-1) {
            throw new IllegalStateException("'.' wasn't found, bad filename: " + filename);
        }
        String ext = filename.substring(idx).toLowerCase();
        return exts.contains(ext);
    }

    @PostMapping(value = "/dataset-upload-dataset-from-file")
    public String createDefinitionFromFile(MultipartFile file, @RequestParam(name = "id") long datasetId) {

        String originFilename = file.getOriginalFilename();
        if (!checkExtension(originFilename)) {
            throw new IllegalStateException("Not supported extension, filename: " + originFilename);
        }

        Optional<Dataset> optionalDataset = repository.findById(datasetId);
        if (!optionalDataset.isPresent()) {
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }
        Dataset dataset = optionalDataset.get();

        List<DatasetPath> paths = pathRepository.findByDataset(dataset);
        //noinspection ConstantConditions
        int pathNumber = paths.isEmpty() ? 1 : paths.stream().mapToInt(DatasetPath::getPathNumber).max().getAsInt() + 1;
        final String path = String.format("%s%c%03d%craws", DEFINITIONS_DIR, File.separatorChar, dataset.getId(), File.separatorChar);

        final File launchpadDir = toFile(launchpadDirAsString);

        File datasetDir = new File(launchpadDir, path);
        if (!datasetDir.exists()) {
            boolean status = datasetDir.mkdirs();
            if (!status) {
                throw new IllegalStateException("Error create directory: " + datasetDir.getAbsolutePath());
            }
        }

        File datasetFile;
        try (InputStream is = file.getInputStream()) {
            datasetFile = new File(datasetDir, String.format("raw-%d-%s", pathNumber, originFilename));
            FileUtils.copyInputStreamToFile(is, datasetFile);
        } catch (IOException e) {
            throw new RuntimeException("error", e);
        }

        DatasetPath dp = new DatasetPath();
        String pathToDataset = path + File.separatorChar + datasetFile.getName();
        dp.setPath(pathToDataset);
        dp.setChecksum(DatasetChecksum.getChecksumAsJson(datasetFile));
        dp.setDataset(dataset);
        dp.setFile(true);
        dp.setPathNumber(pathNumber);
        dp.setValid(true);
        dp.setRegisterTs(new Timestamp(System.currentTimeMillis()));

        pathRepository.save(dp);

        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    private void createColumnsDefinition(Dataset dataset, InputStream is) throws IOException {
        try (final InputStreamReader isr = new InputStreamReader(is, "UTF-8")) {
            try (BufferedReader br = new BufferedReader(isr)) {
                String line = br.readLine();
                DatasetGroup group = new DatasetGroup();
                group.setDescription("Group #1");
                group.setDataset(dataset);
                List<DatasetGroup> groups = new ArrayList<>();
                groups.add(group);
                dataset.setDatasetGroups(groups);

                final AtomicInteger i = new AtomicInteger(1);
                List<DatasetColumn> columns = new ArrayList<>();
                Arrays.stream(line.split("[,]")).filter(s -> s != null && s.length() > 0).map(String::trim).forEach(name -> {
                            final DatasetColumn c = new DatasetColumn();
                            c.setDatasetGroup(group);
                            c.setName(StringUtils.substring(name, 0, 50));
                            c.setDescription(StringUtils.substring("Column #" + (i.getAndAdd(1)) + ", " + name, 0, 250));
                            columns.add(c);
                        }
                );
                columnRepository.saveAll(columns);
            }
        }
    }

    @PostMapping("/dataset-form-commit")
    public String datasetFormCommit(Dataset dataset) {
        repository.save(dataset);
        return "redirect:/launchpad/datasets";
    }

    @PostMapping("/dataset-definition-form-commit")
    public String datasetDefinitionFormCommit(DatasetDefinition datasetDefinition) {
        repository.save(datasetDefinition.dataset);
        return "redirect:/launchpad/dataset-definition/" + datasetDefinition.dataset.getId();
    }

    @GetMapping("/dataset-delete/{id}")
    public String delete(@PathVariable Long id, Model model) {
        final Optional<Dataset> value = repository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        model.addAttribute("dataset", value.get());
        return "/launchpad/dataset-delete";
    }

    @PostMapping("/dataset-delete-commit")
    public String deleteCommit(Long id) {
        repository.deleteById(id);
        return "redirect:/launchpad/datasets";
    }

    @GetMapping("/dataset-path-delete/{id}")
    public String deletePath(@PathVariable Long id) {
        final Optional<DatasetPath> value = pathRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetPath path = value.get();
        Dataset dataset = path.getDataset();
        pathRepository.delete(path);
        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    private Pageable fixPageSize(Pageable pageable) {
        if (pageable.getPageSize() != limit) {
            pageable = PageRequest.of(pageable.getPageNumber(), limit);
        }
        return pageable;
    }

    @Data
    public static class Result {
        public Slice<Dataset> items;
    }

    @Data
    public static class DatasetDefinition {
        public Dataset dataset;
        public List<DatasetPath> paths = new ArrayList<>();
        public String launchpadDirAsString;
        public String datasetDirAsString;

        public DatasetDefinition(Dataset dataset, String launchpadDirAsString, String datasetDirAsString) {
            this.dataset = dataset;
            this.launchpadDirAsString = launchpadDirAsString;
            this.datasetDirAsString = datasetDirAsString;
        }
    }
}
