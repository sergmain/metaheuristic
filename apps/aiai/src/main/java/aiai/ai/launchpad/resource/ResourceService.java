package aiai.ai.launchpad.resource;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.exceptions.StoreNewPartOfRawFileException;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
@Profile("launchpad")
public class ResourceService {

    private final Globals globals;
    private final BinaryDataService binaryDataService;

    public ResourceService(Globals globals, BinaryDataService binaryDataService) {
        this.globals = globals;
        this.binaryDataService = binaryDataService;
    }

    void storeInitialResource(
            String originFilename, File tempFile, String code, String poolCode, String filename) {

        try {
            try (InputStream is = new FileInputStream(tempFile)) {
                binaryDataService.save(
                        is, tempFile.length(), Enums.BinaryDataType.DATA, code, poolCode, true, filename, null);
            }
        } catch (IOException e) {
            throw new StoreNewPartOfRawFileException(tempFile.getPath(), originFilename);
        }
    }

}
