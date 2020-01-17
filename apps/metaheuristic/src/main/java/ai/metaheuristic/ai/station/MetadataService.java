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
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.station.snippet.StationSnippetService;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.launchpad_lookup.LaunchpadLookupConfig;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.metadata.MetadataUtils;
import ai.metaheuristic.ai.yaml.metadata.SnippetDownloadStatusYaml;
import ai.metaheuristic.ai.yaml.metadata.SnippetDownloadStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("station")
@RequiredArgsConstructor
public class MetadataService {

    private final Globals globals;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService;
    private final StationSnippetService stationSnippetService;

    private Metadata metadata = null;

    @Data
    public static class ChecksumState {
        public boolean signatureIsOk;
        public Checksum checksum;
    }

    @Data
    @AllArgsConstructor
    private static class SimpleCache {
        public final LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad;
        public final LaunchpadLookupConfig.Asset asset;
        public final Metadata.LaunchpadInfo launchpadInfo;
        public final File baseResourceDir;
    }

    private Map<String, SimpleCache> simpleCacheMap = new HashMap<>();

    @PostConstruct
    public void init() {
      final File metadataFile = new File(globals.stationDir, Consts.METADATA_YAML_FILE_NAME);
        if (metadataFile.exists()) {
            String yaml = null;
            try {
                yaml = FileUtils.readFileToString(metadataFile, StandardCharsets.UTF_8);
                metadata = MetadataUtils.to(yaml);
            } catch (org.yaml.snakeyaml.reader.ReaderException e) {
                log.error("#815.010 Bad data in " + metadataFile.getAbsolutePath()+"\nYaml:\n" + yaml);
                throw new IllegalStateException("#815.015 Error while loading file: " + metadataFile.getPath(), e);
            } catch (IOException e) {
                log.error("#815.020 Error", e);
                throw new IllegalStateException("#815.025 Error while loading file: " + metadataFile.getPath(), e);
            }
        }
        if (metadata==null) {
            metadata = new Metadata();
        }
        for (Map.Entry<String, LaunchpadLookupExtendedService.LaunchpadLookupExtended> entry : launchpadLookupExtendedService.lookupExtendedMap.entrySet()) {
            launchpadUrlAsCode(entry.getKey());
        }
        // update metadata.yaml file after fixing brocked metas
        updateMetadataFile();
        markAllAsUnverified();
        //noinspection unused
        int i=0;
    }

    public ChecksumState prepareChecksum(String snippetCode, String launchpadUrl, TaskParamsYaml.SnippetConfig snippetConfig) {
        ChecksumState checksumState = new ChecksumState();
        // check requirements of signature
        if (S.b(snippetConfig.checksum)) {
            setSnippetState(launchpadUrl, snippetCode, Enums.SnippetState.signature_not_found);
            checksumState.signatureIsOk = false;
            return checksumState;
        }
        checksumState.checksum = Checksum.fromJson(snippetConfig.checksum);
        boolean isSignature = checksumState.checksum.checksums.entrySet().stream().anyMatch(e -> e.getKey().isSign);
        if (!isSignature) {
            setSnippetState(launchpadUrl, snippetCode, Enums.SnippetState.signature_not_found);
            checksumState.signatureIsOk = false;
            return checksumState;
        }
        checksumState.signatureIsOk = true;
        return checksumState;
    }

    private void markAllAsUnverified() {
        List<SnippetDownloadStatusYaml.Status > statuses = getSnippetDownloadStatusYamlInternal().statuses;
        for (SnippetDownloadStatusYaml.Status status : statuses) {
            if (status.sourcing!=EnumsApi.SnippetSourcing.launchpad) {
                continue;
            }

            final String launchpadUrl = status.launchpadUrl;
            final String snippetCode = status.code;

            setVerifiedStatus(launchpadUrl, snippetCode, false);
        }
    }

    public SnippetDownloadStatusYaml.Status syncSnippetStatus(String launchpadUrl, LaunchpadLookupConfig.Asset asset, final String snippetCode) {
        try {
            syncSnippetStatusInternal(launchpadUrl, asset, snippetCode);
        } catch (Throwable th) {
            log.error("Error in syncSnippetStatus()", th);
            setSnippetState(launchpadUrl, snippetCode, Enums.SnippetState.io_error);
        }
        return getSnippetDownloadStatuses(launchpadUrl, snippetCode);
    }

    private void syncSnippetStatusInternal(String launchpadUrl, LaunchpadLookupConfig.Asset asset, String snippetCode) {
        SnippetDownloadStatusYaml.Status status = getSnippetDownloadStatuses(launchpadUrl, snippetCode);

        if (status==null || status.sourcing != EnumsApi.SnippetSourcing.launchpad || status.verified) {
            return;
        }
        SimpleCache simpleCache = simpleCacheMap.computeIfAbsent(launchpadUrl, o->{
            final Metadata.LaunchpadInfo launchpadInfo = launchpadUrlAsCode(launchpadUrl);
            return new SimpleCache(
                    launchpadLookupExtendedService.lookupExtendedMap.get(launchpadUrl),
                    asset,
                    launchpadInfo,
                    launchpadLookupExtendedService.prepareBaseResourceDir(launchpadInfo)
            );
        });

        StationSnippetService.DownloadedSnippetConfigStatus downloadedSnippetConfigStatus =
                stationSnippetService.downloadSnippetConfig(launchpadUrl, simpleCache.asset, snippetCode, simpleCache.launchpadInfo.stationId);

        if (downloadedSnippetConfigStatus.status==StationSnippetService.ConfigStatus.error) {
            setSnippetState(launchpadUrl, snippetCode, Enums.SnippetState.snippet_config_error);
            return;
        }
        if (downloadedSnippetConfigStatus.status==StationSnippetService.ConfigStatus.not_found) {
            removeSnippet(launchpadUrl, snippetCode);
            return;
        }
        TaskParamsYaml.SnippetConfig snippetConfig = downloadedSnippetConfigStatus.snippetConfig;

        ChecksumState checksumState = prepareChecksum(snippetCode, launchpadUrl, snippetConfig);
        if (!checksumState.signatureIsOk) {
            return;
        }

        final AssetFile assetFile = ResourceUtils.prepareSnippetFile(simpleCache.baseResourceDir, status.code, snippetConfig.file);
        if (assetFile.isError) {
            setSnippetState(launchpadUrl, snippetCode, Enums.SnippetState.asset_error);
            return;
        }
        if (!assetFile.isContent) {
            setSnippetState(launchpadUrl, snippetCode, Enums.SnippetState.none);
            return;
        }

        try {
            CheckSumAndSignatureStatus checkSumAndSignatureStatus = getCheckSumAndSignatureStatus(
                    snippetCode, simpleCache.launchpad.launchpadLookup, checksumState.checksum, assetFile.file);

            if (checkSumAndSignatureStatus.checksum==CheckSumAndSignatureStatus.Status.correct && checkSumAndSignatureStatus.signature==CheckSumAndSignatureStatus.Status.correct) {
                if (status.snippetState!=Enums.SnippetState.ready || !status.verified) {
                    setSnippetState(launchpadUrl, snippetCode, Enums.SnippetState.ready);
                }
            }
        } catch (Throwable th) {
            log.error(S.f("#815.030 Error verifying snippet %s from %s", snippetCode, launchpadUrl), th);
            setSnippetState(launchpadUrl, snippetCode, Enums.SnippetState.io_error);
        }
    }

    public CheckSumAndSignatureStatus getCheckSumAndSignatureStatus(
            String snippetCode, LaunchpadLookupConfig.LaunchpadLookup launchpad, Checksum checksum, File snippetTempFile) throws IOException {
        CheckSumAndSignatureStatus status = new CheckSumAndSignatureStatus(CheckSumAndSignatureStatus.Status.correct, CheckSumAndSignatureStatus.Status.correct);
        if (launchpad.acceptOnlySignedSnippets) {
            try (FileInputStream fis = new FileInputStream(snippetTempFile)) {
                status = ChecksumWithSignatureUtils.verifyChecksumAndSignature(checksum, "Launchpad url: "+ launchpad.url +", snippet: "+snippetCode, fis, true, launchpad.createPublicKey());
            }
            if (status.signature != CheckSumAndSignatureStatus.Status.correct) {
                log.warn("#815.040 launchpad.acceptOnlySignedSnippets is {} but snippet {} has the broken signature", launchpad.acceptOnlySignedSnippets, snippetCode);
                setSnippetState(launchpad.url, snippetCode, Enums.SnippetState.signature_wrong);
            }
            else if (status.checksum != CheckSumAndSignatureStatus.Status.correct) {
                log.warn("#815.050 launchpad.acceptOnlySignedSnippets is {} but snippet {} has the broken signature", launchpad.acceptOnlySignedSnippets, snippetCode);
                setSnippetState(launchpad.url, snippetCode, Enums.SnippetState.checksum_wrong);
            }
        }
        return status;
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
            throw new IllegalStateException("#815.060 launchpadUrl is null");
        }
        if (StringUtils.isBlank(stationId)) {
            throw new IllegalStateException("#815.070 StationId is null");
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

    public List<SnippetDownloadStatusYaml.Status> registerNewSnippetCode(String launchpadUrl, List<LaunchpadCommParamsYaml.Snippets.Info> infos) {
        if (S.b(launchpadUrl)) {
            throw new IllegalStateException("#815.080 launchpadUrl is null");
        }
        SnippetDownloadStatusYaml snippetDownloadStatusYaml;
        synchronized (syncObj) {
            snippetDownloadStatusYaml = getSnippetDownloadStatusYamlInternal();
            boolean isChanged = false;
            for (LaunchpadCommParamsYaml.Snippets.Info info : infos) {
                SnippetDownloadStatusYaml.Status status = snippetDownloadStatusYaml.statuses.stream()
                        .filter(o->o.launchpadUrl.equals(launchpadUrl) && o.code.equals(info.code))
                        .findAny().orElse(null);
                if (status==null || status.snippetState==Enums.SnippetState.not_found) {
                    setSnippetDownloadStatusInternal(launchpadUrl, info.code, info.sourcing, Enums.SnippetState.none);
                    isChanged = true;
                }
            }

            // set state to SnippetState.not_found if snippet doesn't exist on Launchpad any more
            for (SnippetDownloadStatusYaml.Status status : snippetDownloadStatusYaml.statuses) {
                if (status.launchpadUrl.equals(launchpadUrl) && infos.stream().filter(i-> i.code.equals(status.code)).findAny().orElse(null)==null) {
                    setSnippetDownloadStatusInternal(launchpadUrl, status.code, status.sourcing, Enums.SnippetState.not_found);
                    isChanged = true;
                }
            }
            if (isChanged) {
                updateMetadataFile();
            }
        }
        return snippetDownloadStatusYaml.statuses;
    }

    public boolean setSnippetState(final String assetUrl, String snippetCode, Enums.SnippetState snippetState) {
        if (S.b(assetUrl)) {
            throw new IllegalStateException("#815.090 assetUrl is null");
        }
        if (S.b(snippetCode)) {
            throw new IllegalStateException("#815.100 snippetCode is null");
        }
        synchronized (syncObj) {
            SnippetDownloadStatusYaml snippetDownloadStatusYaml = getSnippetDownloadStatusYamlInternal();

            SnippetDownloadStatusYaml.Status status = snippetDownloadStatusYaml.statuses.stream().filter(o->o.launchpadUrl.equals(assetUrl) && o.code.equals(snippetCode)).findAny().orElse(null);
            if (status == null) {
                return false;
            }
            status.snippetState = snippetState;
            status.verified = true;
            String yaml = SnippetDownloadStatusYamlUtils.BASE_YAML_UTILS.toString(snippetDownloadStatusYaml);
            metadata.metadata.put(Consts.META_SNIPPET_DOWNLOAD_STATUS, yaml);
            updateMetadataFile();
            return true;
        }
    }

    public boolean removeSnippet(final String launchpadUrl, String snippetCode) {
        if (S.b(launchpadUrl)) {
            throw new IllegalStateException("#815.110 launchpadUrl is null");
        }
        if (S.b(snippetCode)) {
            throw new IllegalStateException("#815.120 snippetCode is null");
        }
        synchronized (syncObj) {
            SnippetDownloadStatusYaml snippetDownloadStatusYaml = getSnippetDownloadStatusYamlInternal();
            List<SnippetDownloadStatusYaml.Status> statuses = snippetDownloadStatusYaml.statuses.stream().filter(o->!(o.launchpadUrl.equals(launchpadUrl) && o.code.equals(snippetCode))).collect(Collectors.toList());
            if (statuses.size()!=snippetDownloadStatusYaml.statuses.size()) {
                snippetDownloadStatusYaml.statuses = statuses;
                String yaml = SnippetDownloadStatusYamlUtils.BASE_YAML_UTILS.toString(snippetDownloadStatusYaml);
                metadata.metadata.put(Consts.META_SNIPPET_DOWNLOAD_STATUS, yaml);
                updateMetadataFile();
            }
            return true;
        }
    }

    public boolean setVerifiedStatus(final String launchpadUrl, String snippetCode, boolean verified) {
        if (S.b(launchpadUrl)) {
            throw new IllegalStateException("#815.130 launchpadUrl is null");
        }
        if (S.b(snippetCode)) {
            throw new IllegalStateException("#815.140 snippetCode is null");
        }
        synchronized (syncObj) {
            SnippetDownloadStatusYaml snippetDownloadStatusYaml = getSnippetDownloadStatusYamlInternal();

            SnippetDownloadStatusYaml.Status status = snippetDownloadStatusYaml.statuses.stream().filter(o->o.launchpadUrl.equals(launchpadUrl) && o.code.equals(snippetCode)).findAny().orElse(null);
            if (status == null) {
                return false;
            }
            status.verified = verified;
            String yaml = SnippetDownloadStatusYamlUtils.BASE_YAML_UTILS.toString(snippetDownloadStatusYaml);
            metadata.metadata.put(Consts.META_SNIPPET_DOWNLOAD_STATUS, yaml);
            updateMetadataFile();
            return true;
        }
    }

    public void setSnippetDownloadStatus(final String launchpadUrl, String snippetCode, EnumsApi.SnippetSourcing sourcing, Enums.SnippetState snippetState) {
        if (S.b(launchpadUrl)) {
            throw new IllegalStateException("#815.150 launchpadUrl is null");
        }
        if (S.b(snippetCode)) {
            throw new IllegalStateException("#815.160 snippetCode is null");
        }
        synchronized (syncObj) {
            setSnippetDownloadStatusInternal(launchpadUrl, snippetCode, sourcing, snippetState);
            updateMetadataFile();
        }
    }

    public SnippetDownloadStatusYaml.Status getSnippetDownloadStatuses(String launchpadUrl, String snippetCode) {
        synchronized (syncObj) {
            return getSnippetDownloadStatusYamlInternal().statuses.stream()
                    .filter(o->o.launchpadUrl.equals(launchpadUrl) && o.code.equals(snippetCode))
                    .findAny().orElse(null);
        }
    }

    public List<StationCommParamsYaml.SnippetDownloadStatus.Status> getAsSnippetDownloadStatuses(final String launchpadUrl) {
        synchronized (syncObj) {
            return getSnippetDownloadStatusYamlInternal().statuses.stream()
                    .filter(o->o.launchpadUrl.equals(launchpadUrl))
                    .map(o->new StationCommParamsYaml.SnippetDownloadStatus.Status(o.snippetState, o.code))
                    .collect(Collectors.toList());
        }
    }

    private void setSnippetDownloadStatusInternal(String launchpadUrl, String code, EnumsApi.SnippetSourcing sourcing, Enums.SnippetState snippetState) {
        SnippetDownloadStatusYaml snippetDownloadStatusYaml = getSnippetDownloadStatusYamlInternal();

        SnippetDownloadStatusYaml.Status status = snippetDownloadStatusYaml.statuses.stream().filter(o->o.launchpadUrl.equals(launchpadUrl) && o.code.equals(code)).findAny().orElse(null);
        if (status == null) {
            status = new SnippetDownloadStatusYaml.Status(Enums.SnippetState.none, code, launchpadUrl, sourcing, false);
            snippetDownloadStatusYaml.statuses.add(status);
        }
        status.snippetState = snippetState;
        String yaml = SnippetDownloadStatusYamlUtils.BASE_YAML_UTILS.toString(snippetDownloadStatusYaml);
        metadata.metadata.put(Consts.META_SNIPPET_DOWNLOAD_STATUS, yaml);
    }

    public SnippetDownloadStatusYaml getSnippetDownloadStatusYaml() {
        synchronized (syncObj) {
            //noinspection UnnecessaryLocalVariable
            SnippetDownloadStatusYaml snippetDownloadStatusYaml = getSnippetDownloadStatusYamlInternal();
            return snippetDownloadStatusYaml;
        }
    }

    private SnippetDownloadStatusYaml getSnippetDownloadStatusYamlInternal() {
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
        snippetDownloadStatusYaml.statuses.sort(Comparator.comparingInt(c -> c.sourcing.value));
        return snippetDownloadStatusYaml;
    }

    public String getSessionId(final String launchpadUrl) {
        synchronized (syncObj) {
            return getLaunchpadInfo(launchpadUrl).sessionId;
        }
    }

    public void setSessionId(final String launchpadUrl, String sessionId) {
        if (StringUtils.isBlank(launchpadUrl)) {
            throw new IllegalStateException("#815.170 launchpadUrl is null");
        }
        if (StringUtils.isBlank(sessionId)) {
            throw new IllegalStateException("#815.180 sessionId is null");
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
            log.trace("#815.190 Metadata file exists. Make backup");
            File yamlFileBak = new File(globals.stationDir, Consts.METADATA_YAML_FILE_NAME + ".bak");
            //noinspection ResultOfMethodCallIgnored
            yamlFileBak.delete();
            if (metadataFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                metadataFile.renameTo(yamlFileBak);
            }
        }

        try {
            String data = MetadataUtils.toString(metadata);
            FileUtils.write(metadataFile, data, StandardCharsets.UTF_8, false);
            String check = FileUtils.readFileToString(metadataFile, StandardCharsets.UTF_8);
            if (!check.equals(data)) {
                log.warn("#815.200 Metadata was persisted with an error, content is different, size - expected: {}, actual: {}", data.length(), check.length());
            }
        } catch (IOException e) {
            log.error("#815.210 Error", e);
            throw new IllegalStateException("#815.220 Error while writing to file: " + metadataFile.getPath(), e);
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
