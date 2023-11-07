package dk.kb.cdx.workflow;

import static org.junit.jupiter.api.Assertions.fail;

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
 * Unittest that will start workflow with a input file having 2 warc-files.
 * The input file has to be written since it must have full path to the warc-indexer, because the CDX-indexer needs full path.
 * The HTTP call to Outback CDX server is mocked.
 * 
 * When the run is completed it is validated the output of the completed files contains the two warc-files
 * 
 * 
 */
public class CdxIndexerWorkflowTest {

    private static final Logger log = LoggerFactory.getLogger(CdxIndexerWorkflowTest.class);

    public static String DUMMY_FILE="dummy.txt";    
    public static String WARC_INPUT_FILE="warc_file_test_list.txt";
    public static List<String> WARCS = new ArrayList<>(Arrays.asList("IAH-20080430204825-00000-blackbook.arc", "IAH-20080430204825-00000-blackbook.warc.gz"));


    //Write a text file where each line is full path to a warc-file that will be processed by the workflow
    @BeforeEach
    void createWarcInputFile()  throws IOException{

        try {
            String testDataFolder=getTestResourceFolder();
            Path inputFilePath = getWarcFileListPath();            
            Files.delete(inputFilePath); //Delete if exists        
            log.info("Creating input file with warc files to process:"+inputFilePath);

            int fileCount=0;
            for (String warc : WARCS) {    
                String fullPath= testDataFolder+"/warcs/"+warc;            
                Files.write(inputFilePath, (fullPath+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND,StandardOpenOption.CREATE); //new line after each
                fileCount++;
            }
            log.info("Created inputfile completed. #warcfiles=:"+fileCount);

        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }


    @Test
    void testWorkflow()  {

        try {

            //Create the completed WARC file list in same folder
            Path warcFileListPath = getWarcFileListPath();
            
            String parentFolder=getFile(WARC_INPUT_FILE).getParent().toString();            
            String completedFile=parentFolder+"/warc_file.list.COMPLETED.txt";            
            log.info("Completed files will be written to:"+completedFile);
            String cdxServer="http://localhost:8080"; //is never called, is mocket
            int numberOfThreads=2;

            CdxIndexerWorkflow.startWorkers(cdxServer, warcFileListPath.toString(), completedFile, numberOfThreads);
            
        }
        catch(Exception e) {     
            e.printStackTrace();
            fail("workflow run failed");


        }

    }

    private static Path getWarcFileListPath() {
        String testDataFolder=getTestResourceFolder();        
        Path inputFilePath = Path.of(testDataFolder+"/"+WARC_INPUT_FILE);    
        return inputFilePath;
        
    }
    
    private static String getTestResourceFolder() {
        return Resolver.getPathFromClasspath(DUMMY_FILE).toFile().getParent();

    }

    //Use KB-util to resolve file. 
    protected static File getFile(String resource) throws IOException {
        return Resolver.getPathFromClasspath(resource).toFile(); 
    }


}
