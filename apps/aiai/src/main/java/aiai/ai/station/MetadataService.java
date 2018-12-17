package aiai.ai.station;

import aiai.ai.Consts;
import aiai.ai.Globals;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.metadata.MetadataUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Service
@Slf4j
public class MetadataService {

    private final Globals globals;

    private Metadata metadata = new Metadata();

    public MetadataService(Globals globals) {
        this.globals = globals;
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
        //noinspection unused
        int i=0;
    }

    public Metadata.LaunchpadCode launchpadUrlAsCode(String launchpadUrl) {
        return metadata.launchpad.computeIfAbsent(launchpadUrl, v-> asCode(launchpadUrl));
    }

    private static final Object syncObj = new Object();

    public String getStationId() {
        synchronized (syncObj) {
            return metadata.metadata.get(StationConsts.STATION_ID);
        }
    }

    public void setStationId(String stationId) {
        if (StringUtils.isBlank(stationId)) {
            throw new IllegalStateException("StationId is null");
        }
        synchronized (syncObj) {
            metadata.metadata.put(StationConsts.STATION_ID, stationId);
            updateMetadataFile();
        }
    }

    private void updateMetadataFile() {
        final File metadataFile =  new File(globals.stationDir, Consts.METADATA_YAML_FILE_NAME);
        if (metadataFile.exists()) {
            log.info("Metadata file exists. Make backup");
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

    private Metadata.LaunchpadCode asCode(String launchpadUrl) {
        String s = launchpadUrl.toLowerCase();
        if (launchpadUrl.startsWith(Consts.HTTP)) {
            s = s.substring(Consts.HTTP.length());
        }
        else if (launchpadUrl.startsWith(Consts.HTTPS)) {
            s = s.substring(Consts.HTTPS.length());
        }
        s = StringUtils.replaceEach(s, new String[]{".", ":", "/"}, new String[]{"_", "-", "-"});
        return new Metadata.LaunchpadCode(s);
    }

}
