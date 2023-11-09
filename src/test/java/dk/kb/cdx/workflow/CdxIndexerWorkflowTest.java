package dk.kb.cdx.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.kb.util.Resolver;

/**
 * Unittest that will start workflow with a input file having 2 warc-files. The
 * input file has to be written since it must have full path to the
 * warc-indexer, because the CDX-indexer needs full path and this depends on
 * user_home. Will call workflow as a 'dry run'. Warcs will be parsed but CDX
 * result will not be posted to a CDX-server. When the run is completed it is
 * validated the output of the completed files contains the two warc-files
 * 
 * 
 */
public class CdxIndexerWorkflowTest {

    private static final Logger log = LoggerFactory.getLogger(CdxIndexerWorkflowTest.class);

    public static String DUMMY_FILE = "dummy.txt";
    public static String WARC_INPUT_FILE = "warc_file_test_list.txt";
    public static String WARC_OUTPUT_FILE = "warc_file.list.COMPLETED.txt";

    public static List<String> WARCS = new ArrayList<>(Arrays.asList("IAH-20080430204825-00000-blackbook.arc", "IAH-20080430204825-00000-blackbook.warc.gz"));

    // Write a text file where each line is full path to a warc-file that will be
    // processed by the workflow
    @BeforeEach
    void createWarcInputFile() throws IOException {

        String testDataFolder = getTestResourceFolder();
        Path inputFilePath = getWarcInputFileListPath();
        Path completedFilePath = getWarcCompletedFileListPath();
        
        //Clean up before test
        Files.deleteIfExists(inputFilePath);
        Files.deleteIfExists(completedFilePath);
        
        
        log.info("Creating input file with warc files to process:" + inputFilePath);

        int fileCount = 0;
        for (String warc : WARCS) {
            String fullPath = testDataFolder + "/warcs/" + warc;
            Files.write(inputFilePath, (fullPath + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND, StandardOpenOption.CREATE); // new line after each
            fileCount++;
        }
        log.info("Created inputfile completed. #warcfiles=:" + fileCount);
    }

    @Test
    void testWorkflow() {
        try {
            // Create the completed WARC file list in same folder
            Path warcFileListPath = getWarcInputFileListPath();

            // Start workflow
            String parentFolder = getFile(WARC_INPUT_FILE).getParent().toString();
            String completedFile = parentFolder + "/warc_file.list.COMPLETED.txt"; //Dryrun will append .dryrun.txt to filename
            log.info("Completed files will be written to:" + completedFile);
            String cdxServer = "http://localhost:8081/index?badLines=skip"; //dry run, so it not used
            String absolutePaths = "true";
            String numberOfThreads = "2";
            String dryRun = "true";

            CdxIndexerWorkflow.main(cdxServer, warcFileListPath.toString(), completedFile, absolutePaths, numberOfThreads, dryRun);
            
            // Check workflow wrote the two warc-files as completed.
            validatecompletedFile();

        } catch (Exception e) {
            e.printStackTrace();
            fail("workflow run failed:"+e.getMessage());
        }
    }

    private void validatecompletedFile() throws IOException {
        List<String> allLines = Files.readAllLines(Paths.get(getTestResourceFolder() + "/" + WARC_OUTPUT_FILE +CdxIndexerWorkflow.DRYRUN_SUFFIX));
        assertEquals(WARCS.size(), allLines.size(), "Completed warc files does not have expected number of lines");
        for (String warc : WARCS) {
            String absolutePath = Paths.get(getTestResourceFolder() + "/warcs/" + warc).toString();
            assertTrue(allLines.contains(absolutePath), " Completed list does not include:" + warc);
        }
    }

    private static Path getWarcInputFileListPath() {
        String testDataFolder = getTestResourceFolder();
        Path inputFilePath = Path.of(testDataFolder + "/" + WARC_INPUT_FILE);
        return inputFilePath;

    }

    private static Path getWarcCompletedFileListPath() {
        String testDataFolder = getTestResourceFolder();
        Path inputFilePath = Path.of(testDataFolder + "/" + WARC_OUTPUT_FILE);
        return inputFilePath;

    }

    
    private static String getTestResourceFolder() {
        return Resolver.getPathFromClasspath(DUMMY_FILE).toFile().getParent();

    }

    // Use KB-util to resolve file.
    protected static File getFile(String resource) throws IOException {
        return Resolver.getPathFromClasspath(resource).toFile();
    }

}
