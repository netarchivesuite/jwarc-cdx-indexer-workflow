package dk.kb.cdx.workflow;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.netpreserve.jwarc.cdx.CdxFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**  
* <p>
* The workflow will use the 3 settings for the CDX-indexer automatic.  (digest-unchanged, post-append, warc-full-path, include-revisits)
* <p>
* The workflow takes 6 arguments.
* <ul>
* <li> Number of threads </li>
* <li>  URL to CDX-server </li>
* <li> Text file will list of WARC-files to index. (Full filepath, one WARC file on each line) </li>
* <li> Text file to output completed WARC-files. File will be created if it does not exist </li>
* <li> Number of threads to use for the workflow. </li>
* <li> Dry run. If try will not post CDX-data to CDX-server. Use for test mode </li>
* </ul>
* <p>
* If the indexing workflow is interrupted and stopped, it can just be restarted with the same input WARC-file. It will skip all WARC-files that are listed in the output completed file.
* If the CDX server does not return a http status. (no connection, server dead etc), then the thread will terminate and log this event. This is to avoid 'processing' and mark then completed when they will fail. 
* Some WARC-files will return HTTP error status from the CDX-server, but this is expected and due to corrupt WARC-files. This is mostly old ARC files with http-header errors.
*
* <p>
* Starting the workflow.
* Configure the yaml property file with the 6 properties
* 
* <p>*
* Call the start script:
* bin/start-script.sh
*
* <p>
* Implementation details:
* The list of WARC files to process is read from the input file and stored in List<String>.
* The list of WARC files completed is stored in the output file file stored in HashSet<String> so the contains() method is fast.
*
* <p>
* A synchronized method 'getNextWarcFile' will return next file to process when a thread require a new file.
* If the file is already in the completed set it will just skip returning it and instead try same check for the next file.
* When a WARC file has been completed it will be written to the output file and also add to the memory Set of completed files.
* <p>
* Since the job will take months to complete, regular check not too many threads has been stopped with:
* less cdx_indexer_workflow.log | grep 'Stopping thread'
* A thread will stop if the response from the CDX-server is not expected.
* So far it has never happened unless when forced by stopping the CDX-server for testing.
* <p>
* Expected response from CDX-server: Added 179960 records
*/


public class CdxIndexerWorkflow {
    private static final Logger log = LoggerFactory.getLogger(CdxIndexerWorkflow.class);
    public static final String DRYRUN_SUFFIX=".dryrun.txt";

    private static int NUMBER_OF_THREADS=6;
    private static  String INPUT_WARCS_FILE_LIST=null;
    private static String OUTPUT_WARCS_COMPLETED_FILE_LIST=null;
    private static List<String> WARCS_TO_INDEX= new ArrayList<String>();
    private static HashSet<String> WARCS_COMPLETED= new HashSet <String>();
    private static boolean DRYRUN=false;
    private static String CDX_SERVER=null;
    private static boolean ABSOLUTE_PATH=false;

    //String cdxServer, String inputFile, String outoutFile, int numberOfThreads, boolean dryRun
    //TODO javadoc
    public static void main(String... args) throws Exception {
        checkJavaVersion();
        CDX_SERVER=args[0];
        INPUT_WARCS_FILE_LIST=args[1];
        OUTPUT_WARCS_COMPLETED_FILE_LIST=args[2];
        ABSOLUTE_PATH=Boolean.parseBoolean(args[3]);
        NUMBER_OF_THREADS=Integer.parseInt(args[4]);
        DRYRUN=Boolean.parseBoolean(args[5]);

        if (DRYRUN) {
            OUTPUT_WARCS_COMPLETED_FILE_LIST += DRYRUN_SUFFIX;
            log.info("For dryrun outout file name has been changed to:"+OUTPUT_WARCS_COMPLETED_FILE_LIST);
        }
        
        startWorkers();                
    }

    private static void startWorkers()  throws Exception{ 

        long start=System.currentTimeMillis();
        
        try {
            loadWarcFilesToProcess();            
        }
        catch (Exception e) {            
            log.error("Error starting workers. Could not load list of WARC files or list of completed WARC files. Input file={}, Output file={}", INPUT_WARCS_FILE_LIST,OUTPUT_WARCS_COMPLETED_FILE_LIST ); // also log to console
            System.out.println("Error starting workers. Could not load list of WARC files or list of completed WARC files. See log file");
            e.printStackTrace();
            System.exit(1); 
        }
        log.info("Input WARC-file size:"+WARCS_TO_INDEX.size());
        log.info("Already completed WARC-file size:"+WARCS_COMPLETED.size());
        log.info("Starting indexing with number of threads:"+NUMBER_OF_THREADS);

        CdxFormat.Builder cdxFormatBuilder = createCdxBuilder();


        ExecutorService executor = Executors.newCachedThreadPool();

        //Start all workers
        ArrayList<CdxIndexWorker> workerList = new ArrayList<CdxIndexWorker>();
        for (int threadNumber=0;threadNumber<NUMBER_OF_THREADS;threadNumber++){
            CdxIndexWorker  worker =  new CdxIndexWorker(CDX_SERVER, cdxFormatBuilder,ABSOLUTE_PATH,threadNumber, DRYRUN);
            workerList.add(worker);
                                
        }            
        List<Future<WorkerStatus>> results = executor.invokeAll(workerList);
        
        printWorkflowStatistics(results);
        log.info("Workflow completed, run time in millis:"+(System.currentTimeMillis()-start));                
    }

    
    private static void printWorkflowStatistics( List<Future<WorkerStatus>>  futures) { 
    try {
        int totalCompleted=0;
        int totalErrors=0;
        for (Future<WorkerStatus> f: futures) {
            WorkerStatus status = f.get();
            totalCompleted += status.getCompleted();
            totalErrors += status.getErrors();            
        }
        
        log.info("Total number of WARC-files processed:"+totalCompleted);
        log.info("Total number of errors encounted:"+totalErrors);           
     }
     catch(Exception e) {
        log.error("Error logging workflow statistics after run completed"); //Should never happen...
     }                       
    }
    
    /**
     * JWarc will fail runtime with java8. Is there a better way to detect this?
     * 
     */    
    private static void checkJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("8.0") || version.toString().startsWith("1.8")){
            log.error("Must use java 11 or 17. Runtime version is:"+version);
            System.out.println("Must use java 11 or 17. Runtime version is:"+version);
            System.exit(1);    
        }  
    }

    /**
     * Will load input WARC-files and output-file of those processed
     * 
     * @throws Exception If input or output file can not be read
     */
    private static void loadWarcFilesToProcess() throws IOException{
        WARCS_TO_INDEX = readInputWarcList(INPUT_WARCS_FILE_LIST);
        WARCS_COMPLETED = readCompletedWarcs(OUTPUT_WARCS_COMPLETED_FILE_LIST);
    }

    public static synchronized String getNextWarcFile() {

        //To avoid deep stack trace, using while construction instead of recursive method call
        while (WARCS_TO_INDEX.size() != 0) {

            String next = WARCS_TO_INDEX.remove(0);

            //Check it is no already processed. (can happen if run was interrupted and restarted)
            if (WARCS_COMPLETED.contains(next)) {
                log.debug("Skipping, already processed:"+next);
                continue;
            }

            //Skip metadata. This is some custom Netarchive Suite/Heritrix information that does not belong in CDX-indexer
            if (next.contains("metadata")) {
                log.debug("Skipping metadata file:"+next);
                continue;
            }
            return next;
        }

        return null;
    }


    public static synchronized void markWarcFileCompleted(String warcFile) throws IOException{              
        try {
            WARCS_COMPLETED.add(warcFile); //Add to completed memory HashSet         
            Path completedPath=  Paths.get(OUTPUT_WARCS_COMPLETED_FILE_LIST);      

            //Append to a line to the file. Will create file if it does not exist
            Files.writeString(completedPath, (warcFile+"\n"),StandardOpenOption.APPEND,StandardOpenOption.CREATE); 
        }
        catch(Exception e) {
            log.error("Error marking warc file as completed:"+warcFile);
            throw new IOException(e);
        }

    }

    private  static List<String> readInputWarcList(String file) throws IOException{        
        try {
            List<String> allLines = Files.readAllLines(Paths.get(file));
            return allLines;  

        } catch (IOException e) {
            throw new IOException("Could not read from file:"+file);
        }
    }    


    private static HashSet<String> readCompletedWarcs(String file) throws IOException{        
        try {
            File outputFile = new File(file);  
            if (!outputFile.exists()) {
                outputFile.createNewFile();
                log.info("Created new empty WARCS completed file:"+file);
            }

            List<String> allLines = Files.readAllLines(Paths.get(file));
            HashSet<String> completedWarcs= new HashSet<String>();            
            completedWarcs.addAll(allLines); //Yes, will take double memory for a brief moment            
            return completedWarcs;


        } catch (IOException e) {
            throw new IOException("Could not read from file:"+file);
        }
    }    

    private static CdxFormat.Builder createCdxBuilder() {
        CdxFormat.Builder cdxFormatBuilder = new CdxFormat.Builder().        
                digestUnchanged().                
                legend(CdxFormat.CDX11_LEGEND);

        return cdxFormatBuilder;
    }


}
