package aiai.ai.utils;

import aiai.ai.Consts;
import aiai.ai.station.StationTaskService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Slf4j
public class TestDeleteOrRenameDir {

    private static Random r = new Random();

    @Test
    public void testDeleteOrRenameDir() throws IOException {
        String tempDirName = System.getProperty("java.io.tmpdir");

        File tempDir = new File(tempDirName);
        log.info("tempDir: {}", tempDir.getPath());
        assertTrue(tempDir.exists());
        assertTrue(tempDir.isDirectory());

        File d = new File(tempDir,"temp-dir-"+r.nextInt(100)+System.nanoTime());
        log.info("d: {}", d.getPath());
        assertFalse(d.exists());
        d.mkdirs();
        assertTrue(d.exists());
        assertTrue(d.isDirectory());


        File f = new File(d, Consts.TASK_YAML);
        log.info("f: {}", f.getPath());
        f.createNewFile();
        assertTrue(f.exists());
        assertTrue(f.isFile());

        boolean status = StationTaskService.deleteOrRenameTaskDir(d, f);
        assertTrue(status);
        assertFalse(d.exists());
    }

}
