package aiai.ai.station;

import aiai.ai.Consts;
import aiai.ai.Globals;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.metadata.MetadataUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

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
        if (!metadataFile.exists()) {
            log.warn("Station's metadata file doesn't exist: {}", metadataFile.getPath());
            return;
        }
        try(FileInputStream fis = new FileInputStream(metadataFile)) {
            metadata = MetadataUtils.to(fis);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while loading file: " + metadataFile.getPath(), e);
        }
        for (Map.Entry<String, LaunchpadLookupExtendedService.LaunchpadLookupExtended> entry : launchpadLookupExtendedService.lookupExtendedMap.entrySet()) {
            launchpadUrlAsCode(entry.getKey());
        }
        updateMetadataFile();
        //noinspection unused
        int i=0;
    }

    public String findHostByCode(String code) {
        for (Map.Entry<String, Metadata.LaunchpadInfo> entry : metadata.launchpad.entrySet()) {
            if (code.equals(entry.getValue().code)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Metadata.LaunchpadInfo launchpadUrlAsCode(String launchpadUrl) {
        return metadata.launchpad.computeIfAbsent(launchpadUrl, v-> asCode(launchpadUrl));
    }

    private static final Object syncObj = new Object();

    public String getStationId(String launchpadUrl) {
        synchronized (syncObj) {
            return metadata.launchpad.computeIfAbsent(launchpadUrl, m->new Metadata.LaunchpadInfo()).stationId;
        }
    }

    public void setStationId(String launchpadUrl, String stationId) {
        if (StringUtils.isBlank(launchpadUrl)) {
            throw new IllegalStateException("launchpadUrl is null");
        }
        if (StringUtils.isBlank(stationId)) {
            throw new IllegalStateException("StationId is null");
        }
        synchronized (syncObj) {
            metadata.launchpad.computeIfAbsent(launchpadUrl, m->new Metadata.LaunchpadInfo()).stationId = stationId;
            updateMetadataFile();
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
            FileUtils.write(metadataFile, MetadataUtils.toString(metadata), Charsets.UTF_8, false);
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
