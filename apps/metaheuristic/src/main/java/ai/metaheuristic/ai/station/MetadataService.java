/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.station;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.S;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.metadata.MetadataUtils;
import ai.metaheuristic.ai.yaml.metadata.SnippetDownloadStatusYaml;
import ai.metaheuristic.ai.yaml.metadata.SnippetDownloadStatusYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("station")
public class MetadataService {

    private final Globals globals;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService;

    private Metadata metadata = new Metadata();

    public MetadataService(Globals globals, LaunchpadLookupExtendedService launchpadLookupExtendedService) {
        this.globals = globals;
        this.launchpadLookupExtendedService = launchpadLookupExtendedService;
    }

    @PostConstruct
    public void init() {
      final File metadataFile = new File(globals.stationDir, Consts.METADATA_YAML_FILE_NAME);
        if (metadataFile.exists()) {
            try (FileInputStream fis = new FileInputStream(metadataFile)) {
                metadata = MetadataUtils.to(fis);
            } catch (IOException e) {
                log.error("Error", e);
                throw new IllegalStateException("Error while loading file: " + metadataFile.getPath(), e);
            }
        }
        for (Map.Entry<String, LaunchpadLookupExtendedService.LaunchpadLookupExtended> entry : launchpadLookupExtendedService.lookupExtendedMap.entrySet()) {
            launchpadUrlAsCode(entry.getKey());
        }
        updateMetadataFile();
        //noinspection unused
        int i=0;
    }

    public String findHostByCode(String code) {
        synchronized (syncObj) {
            for (Map.Entry<String, Metadata.LaunchpadInfo> entry : metadata.launchpad.entrySet()) {
                if (code.equals(entry.getValue().code)) {
                    return entry.getKey();
                }
            }
            return null;
        }
    }

    private static final Object syncObj = new Object();

    public Metadata.LaunchpadInfo launchpadUrlAsCode(String launchpadUrl) {
        synchronized (syncObj) {
            Metadata.LaunchpadInfo launchpadInfo = getLaunchpadInfo(launchpadUrl);
            // fix for wrong metadata.yaml data
            if (launchpadInfo.code == null) {
                launchpadInfo.code = asCode(launchpadUrl).code;
                updateMetadataFile();
            }
            return launchpadInfo;
        }
    }

    public String getStationId(final String launchpadUrl) {
        synchronized (syncObj) {
            return getLaunchpadInfo(launchpadUrl).stationId;
        }
    }

    public void setStationIdAndSessionId(final String launchpadUrl, String stationId, String sessionId) {
        if (StringUtils.isBlank(launchpadUrl)) {
            throw new IllegalStateException("launchpadUrl is null");
        }
        if (StringUtils.isBlank(stationId)) {
            throw new IllegalStateException("StationId is null");
        }
        synchronized (syncObj) {
            final Metadata.LaunchpadInfo launchpadInfo = getLaunchpadInfo(launchpadUrl);
            if (!Objects.equals(launchpadInfo.stationId, stationId) || !Objects.equals(launchpadInfo.sessionId, sessionId)) {
                launchpadInfo.stationId = stationId;
                launchpadInfo.sessionId = sessionId;
                updateMetadataFile();
            }
        }
    }

    public List<SnippetDownloadStatusYaml.Status> registerNewSnippetCode(String launchpadUrl, List<String> codes) {
        if (S.b(launchpadUrl)) {
            throw new IllegalStateException("launchpadUrl is null");
        }
        SnippetDownloadStatusYaml snippetDownloadStatusYaml;
        synchronized (syncObj) {
            snippetDownloadStatusYaml = getSnippetDownloadStatusYaml();
            boolean isChanged = false;
            for (String code : codes) {
                SnippetDownloadStatusYaml.Status status = snippetDownloadStatusYaml.statuses.stream()
                        .filter(o->o.launchpadUrl.equals(launchpadUrl) && o.code.equals(code))
                        .findAny().orElse(null);
                if (status==null) {
                    setSnippetDownloadStatusInternal(launchpadUrl, code, Enums.SnippetState.none);
                    isChanged = true;
                }
            }
            for (SnippetDownloadStatusYaml.Status status : snippetDownloadStatusYaml.statuses) {
                if (status.launchpadUrl.equals(launchpadUrl) && !codes.contains(status.code)) {
                    setSnippetDownloadStatusInternal(launchpadUrl, status.code, Enums.SnippetState.not_found);
                    isChanged = true;
                }
            }
            if (isChanged) {
                updateMetadataFile();
            }
        }
        return snippetDownloadStatusYaml.statuses;
    }

    public void setSnippetDownloadStatus(final String launchpadUrl, String snippetCode, Enums.SnippetState snippetState) {
        if (S.b(launchpadUrl)) {
            throw new IllegalStateException("launchpadUrl is null");
        }
        if (S.b(snippetCode)) {
            throw new IllegalStateException("snippetCode is null");
        }
        synchronized (syncObj) {
            setSnippetDownloadStatusInternal(launchpadUrl, snippetCode, snippetState);
            updateMetadataFile();
        }
    }

    public List<StationCommParamsYaml.SnippetDownloadStatus.Status> getAsSnippetDownloadStatuses() {
        synchronized (syncObj) {
            return getSnippetDownloadStatusYaml().statuses.stream()
                    .map(o->new StationCommParamsYaml.SnippetDownloadStatus.Status(o.snippetState, o.code))
                    .collect(Collectors.toList());
        }
    }

    private void setSnippetDownloadStatusInternal(String launchpadUrl, String snippetCode, Enums.SnippetState snippetState) {
        SnippetDownloadStatusYaml snippetDownloadStatusYaml = getSnippetDownloadStatusYaml();

        SnippetDownloadStatusYaml.Status status = snippetDownloadStatusYaml.statuses.stream().filter(o->o.launchpadUrl.equals(launchpadUrl) && o.code.equals(snippetCode)).findAny().orElse(null);
        if (status == null) {
            status = new SnippetDownloadStatusYaml.Status(Enums.SnippetState.none, snippetCode, launchpadUrl);
            snippetDownloadStatusYaml.statuses.add(status);
        }
        status.snippetState = snippetState;
        String yaml = SnippetDownloadStatusYamlUtils.BASE_YAML_UTILS.toString(snippetDownloadStatusYaml);
        metadata.metadata.put(Consts.META_SNIPPET_DOWNLOAD_STATUS, yaml);
    }

    private SnippetDownloadStatusYaml getSnippetDownloadStatusYaml() {
        String yaml = metadata.metadata.get(Consts.META_SNIPPET_DOWNLOAD_STATUS);
        SnippetDownloadStatusYaml snippetDownloadStatusYaml;
        if (S.b(yaml)) {
            snippetDownloadStatusYaml = new SnippetDownloadStatusYaml();
        }
        else {
            snippetDownloadStatusYaml = SnippetDownloadStatusYamlUtils.BASE_YAML_UTILS.to(yaml);
            if (snippetDownloadStatusYaml == null) {
                snippetDownloadStatusYaml = new SnippetDownloadStatusYaml();
            }
        }
        return snippetDownloadStatusYaml;
    }

    public String getSessionId(final String launchpadUrl) {
        synchronized (syncObj) {
            return getLaunchpadInfo(launchpadUrl).sessionId;
        }
    }

    public void setSessionId(final String launchpadUrl, String sessionId) {
        if (StringUtils.isBlank(launchpadUrl)) {
            throw new IllegalStateException("launchpadUrl is null");
        }
        if (StringUtils.isBlank(sessionId)) {
            throw new IllegalStateException("sessionId is null");
        }
        synchronized (syncObj) {
            getLaunchpadInfo(launchpadUrl).stationId = sessionId;
            updateMetadataFile();
        }
    }

    private Metadata.LaunchpadInfo getLaunchpadInfo(String launchpadUrl) {
        synchronized (syncObj) {
            return metadata.launchpad.computeIfAbsent(launchpadUrl, m -> asCode(launchpadUrl));
        }
    }

    private void updateMetadataFile() {
        final File metadataFile =  new File(globals.stationDir, Consts.METADATA_YAML_FILE_NAME);
        if (metadataFile.exists()) {
            log.trace("Metadata file exists. Make backup");
            File yamlFileBak = new File(globals.stationDir, Consts.METADATA_YAML_FILE_NAME + ".bak");
            //noinspection ResultOfMethodCallIgnored
            yamlFileBak.delete();
            if (metadataFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                metadataFile.renameTo(yamlFileBak);
            }
        }

        try {
            FileUtils.write(metadataFile, MetadataUtils.toString(metadata), StandardCharsets.UTF_8, false);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while writing to file: " + metadataFile.getPath(), e);
        }
    }

    private Metadata.LaunchpadInfo asCode(String launchpadUrl) {
        String s = launchpadUrl.toLowerCase();
        if (launchpadUrl.startsWith(Consts.HTTP)) {
            s = s.substring(Consts.HTTP.length());
        }
        else if (launchpadUrl.startsWith(Consts.HTTPS)) {
            s = s.substring(Consts.HTTPS.length());
        }
        s = StringUtils.replaceEach(s, new String[]{".", ":", "/"}, new String[]{"_", "-", "-"});
        Metadata.LaunchpadInfo launchpadInfo = new Metadata.LaunchpadInfo();
        launchpadInfo.code = s;
        return launchpadInfo;
    }

}
