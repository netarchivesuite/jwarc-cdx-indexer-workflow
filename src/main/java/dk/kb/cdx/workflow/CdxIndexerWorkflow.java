package dk.kb.cdx.workflow;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.netpreserve.jwarc.cdx.CdxFormat;
import org.netpreserve.jwarc.cdx.CdxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/* 
The workflow will use the 3 settings for the CDX-indexer automatic.  (digest-unchanged, post-append, warc-full-path, include-revisits)

The workflow takes 4 arguments.
1) Number of threads
2) URL to CDX-server
3) Text file will list of WARC-files to index. (Full filepath, one WARC file on each line)
4) Text file to output completed WARC-files.

If the indexing workflow is interrupted and stopped, it can just be restarted with the same input WARC-file. It will skip all WARC-files that are listed in the output completed file.
If the CDX server does not return a http status. (no connection, server dead etc), then the thread will terminate and log this event. This is to avoid 'processing' and mark then completed when they will fail. 
Some WARC-files will return HTTP error status from the CDX-server, but this is expected and due to corrupt WARC-files. This is mostly old ARC files with http-header errors.


Start workflow (24 threads) (replace server url)
setsid nohup java -Xmx16g -cp jwarc.jar  org.netpreserve.jwarc.workflows.CdxIndexerWorkflow 24 http://server.com:8081/index?badLines=skip /netarkiv-cdx/netarkivet.files.20230705  /netarkiv-cdx/netarkivet.files.20230705.COMPLETED.txt 2>&1 >> cdx_indexer_workflow.lo
(jwarc.jar will have SNAPSHOT in filename when build, just rename)

Build project with mvn build

Implementation details:
The list of WARC files to process is read from the input file and stored in List<String>.
The list of WARC files completed is stored in the output file file stored in HashSet<String> so the contains method is fast.

A syncronized method 'getNextWarcFile' will return next file to process when a thread require a new file.
If the file is already in the completed set it will just skip returning it and instead try same check for the next file.
When a WARC file has been completed it will be written to the output file and also add to the memory Set of completed files.

Since the job will take months to complete, regular check not too many threads has been stopped with:
less cdx_indexer_workflow.log | grep 'Stopping thread'
So far it has never happened unless when forced by stopping the CDX-server for testing.

 */
public class CdxIndexerWorkflow {
    private static final Logger log = LoggerFactory.getLogger(CdxIndexerWorkflow.class);
    
    
    private static int NUMBER_OF_THREADS=6;
    private static  String CDX_SERVER=null;
    private static  String INPUT_WARCS_FILE_LIST=null;
    private static String OUTPUT_WARCS_COMPLETED_FILE_LIST=null;
    private static List<String> WARCS_TO_INDEX= new ArrayList<String>();
    private static HashSet<String> WARCS_COMPLETED= new HashSet <String>();

    public static void startWorkers(String cdxServer, String inputFile, String outoutFile, int numberOfThreads )  throws Exception{ 
        checkJavaVersion();        
        NUMBER_OF_THREADS =numberOfThreads;
        CDX_SERVER = cdxServer;                    
        INPUT_WARCS_FILE_LIST = inputFile;        
        OUTPUT_WARCS_COMPLETED_FILE_LIST = outoutFile;                     
        
        try {
            init();            
        }
        catch (Exception e) {            
            log.error("Error starting workers. Could not load list of WARC files or list of completed WARC files. Input file={}, Output file={}",inputFile,outoutFile ); // also log to console
            System.out.println("Error starting workers. Could not load list of WARC files or list of completed WARC files. See log file");
            e.printStackTrace();
            System.exit(1); 
        }
        log.info("Input WARC-file size:"+WARCS_TO_INDEX.size());
        log.info("Already completed WARC-file size::"+WARCS_COMPLETED.size());
        log.info("Starting indexing with number of threads:"+NUMBER_OF_THREADS);

        CdxFormat.Builder cdxFormatBuilder = createCdxBuilder();

        //Start all workers
        for (int threadNumber=0;threadNumber<NUMBER_OF_THREADS;threadNumber++){
            CdxIndexerWorkerThread  thread =  new CdxIndexerWorkflow().new CdxIndexerWorkerThread(cdxFormatBuilder,threadNumber);
            thread.start();
        }                  
    }
    
    /**
     * JWarc will fail runtime with java8. 
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
    private static void init() throws Exception{
        WARCS_TO_INDEX = readInputWarcList(INPUT_WARCS_FILE_LIST);
        WARCS_COMPLETED = readCompletedWarcs(OUTPUT_WARCS_COMPLETED_FILE_LIST);
    }

    private static synchronized String getNextWarcFile() {

        //To avoid deep stack trace, using while construction instead of recursive method call
        while (WARCS_TO_INDEX.size() != 0) {

            String next =WARCS_TO_INDEX.remove(0);

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


    private static String getCdxOutput(String warcFile, CdxFormat.Builder cdxFormatBuilder ) throws IOException {
        List<Path> files = new ArrayList<Path>();
        files.add( new File(warcFile).toPath());
        try (StringWriter stringWriter = new StringWriter();CdxWriter cdxWriter = new CdxWriter(stringWriter); ) {           
           cdxWriter.setPostAppend(true); //very important for PyWb SOME playback
           cdxWriter.setFormat(cdxFormatBuilder.build());
           cdxWriter.writeHeaderLine();
           cdxWriter.onWarning(log::error);
           cdxWriter.process(files, true);
        return stringWriter.toString();               
        }
    }

    /*
     * Return the body message from the CDX server. If everything is well it will be something like: 'Added 80918 records'
     * Will log error if HTTP status is not 200
     */
    private static String postCdxToServer(String cdxServer, String data) throws Exception {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cdxServer))
                .POST(BodyPublishers.ofString(data))
                .build();

        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());        
        int status=response.statusCode();
        String body=response.body().toString();
        if (status != 200) {            
            log.error("Unexpected http status:"+status +" with body:"+body);            
        }

        return body;                 
    }

    private synchronized void markWarcFileCompleted(String warcFile) throws IOException{              
        try {
            WARCS_COMPLETED.add(warcFile); //Add to completed memory HashSet         
            Path completedPath=  Paths.get(OUTPUT_WARCS_COMPLETED_FILE_LIST);        
            Files.write(completedPath, (warcFile+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND); //new line after each

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

    public class CdxIndexerWorkerThread extends Thread {
        private int threadNumber;
        private int numberProcessed;
        private int numberErrors;
        CdxFormat.Builder cdxFormatBuilder;

        public CdxIndexerWorkerThread(   CdxFormat.Builder cdxFormatBuilder,int threadNumber){
            this.threadNumber=threadNumber;
            this.cdxFormatBuilder = cdxFormatBuilder;
            this.setDaemon(true); //Required or the stand-alone-template will exit
        }    

        public void run() {
            log.info("Starting CdxIndexerWorkerThread:"+threadNumber);                        

            String nextWarcFile=getNextWarcFile();
            while( nextWarcFile != null ){     
                try{
                    String cdxOutput=getCdxOutput(nextWarcFile, cdxFormatBuilder); //Exceptions are acceptable, can be corrupt WARC-files.
                    String responseBody=null;
                    try {
                       responseBody=postCdxToServer(CDX_SERVER, cdxOutput); //Critital this does not fail. Stop thread instead of continue with something that can fail again and again
                    }
                    catch(Exception e) { //stop thread if CDX server is not running as expected. 
                        
                     log.error("Stopping thread:"+threadNumber + " Error connecting to CDX server:"+e.getMessage());  
                     return;                     
                    }
                   if (responseBody != null) {
                       responseBody= responseBody.trim(); //Remove a new line from the server as last character
                   }
                    
                    numberProcessed++;
                    log.info("Indexed:"+nextWarcFile +" result:"+responseBody); 
                    markWarcFileCompleted(nextWarcFile);
                }
                catch(Exception e){
                    numberErrors++;
                    log.error("Error processing:"+nextWarcFile +": "+e.getMessage());
                    try {
                      markWarcFileCompleted(nextWarcFile);
                    }
                    catch(Exception exceptionIO) {                        
                       log.error("Error marking WARC file as completed. Stopping thread. WarcFile:"+nextWarcFile);
                       return;//Stop worker! 
                    }
                }
                nextWarcFile= getNextWarcFile();            
            }
            log.info("Worker completed. No more WARC-files to process for CdxIndexerWorkerThread:"+threadNumber + ". Number processed:"+numberProcessed +" Number of errors:"+numberErrors);                        
        }
    }

}
