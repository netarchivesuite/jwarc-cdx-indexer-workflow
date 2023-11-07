package dk.kb.cdx.workflow;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.netpreserve.jwarc.cdx.CdxFormat;
import org.netpreserve.jwarc.cdx.CdxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




public class CdxIndexWorker extends Thread{
    
    private static final Logger log = LoggerFactory.getLogger(CdxIndexWorker.class);
       
    private int threadNumber;
    private int numberProcessed;
    private int numberErrors;
    private  String cdxServerUrl=null;
    CdxFormat.Builder cdxFormatBuilder;

    /*
     * This is not a daemon thread. The thread will continue to run while main program is waiting for them to complete before finishing and exit with exitCode=0
     * 
     */
    public CdxIndexWorker( String cdxServerUrl, CdxFormat.Builder cdxFormatBuilder,int threadNumber){
        this.threadNumber=threadNumber;
        this.cdxFormatBuilder = cdxFormatBuilder;
        this.cdxServerUrl=cdxServerUrl;
    }    
    
    

    public void run() {
        log.info("Starting CdxIndexerWorkerThread:"+threadNumber);                        

        String nextWarcFile=CdxIndexerWorkflow.getNextWarcFile();
        while( nextWarcFile != null ){     
            try{
                String cdxOutput=getCdxOutput(nextWarcFile, cdxFormatBuilder); //Exceptions are acceptable, can be corrupt WARC-files.
                String responseBody=null;
                try {
                   responseBody=postCdxToServer(cdxServerUrl, cdxOutput); //Critital this does not fail. Stop thread instead of continue with something that can fail again and again
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
                CdxIndexerWorkflow.markWarcFileCompleted(nextWarcFile);
            }
            catch(Exception e){
                numberErrors++;
                log.error("Error processing WARC-file:"+nextWarcFile +": "+e.getMessage());
                try {
                    CdxIndexerWorkflow.markWarcFileCompleted(nextWarcFile);
                }
                catch(Exception eIO) {                        
                   log.error("Error marking WARC file as completed. Stopping thread. WarcFile:"+nextWarcFile,eIO); // Has not happened yet
                   return;//Stop worker! 
                }
            }
            nextWarcFile= CdxIndexerWorkflow.getNextWarcFile();            
        }
        log.info("Worker completed. No more WARC-files to process for CdxIndexerWorkerThread:"+threadNumber + ". Number processed:"+numberProcessed +" Number of errors:"+numberErrors);                        
    }
    
    /*
     * Return the body message from the CDX server. If everything is well it will be something like: 'Added 80918 records'
     * Will log error if HTTP status is not 200
     */
    private static String postCdxToServer(String cdxServer, String data) throws IOException,InterruptedException {
     
        System.out.println("calling server");
        
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
            throw new IOException("Unexpected http status:"+status +" with body:"+body);
        }

        return body;                 
    }

    
    
    private static String getCdxOutput(String warcFile, CdxFormat.Builder cdxFormatBuilder ) throws IOException {
        File file=new File(warcFile);
        if(!file.exists()) {
            throw new IOException("WARC file not found:"+warcFile);
        }
        
        List<Path> files = new ArrayList<Path>();
        files.add(file.toPath());
        try (StringWriter stringWriter = new StringWriter();CdxWriter cdxWriter = new CdxWriter(stringWriter); ) {           
           cdxWriter.setPostAppend(true); //very important for PyWb SOME playback
           cdxWriter.setFormat(cdxFormatBuilder.build());
           cdxWriter.writeHeaderLine();
           cdxWriter.onWarning(log::error); // Use the current logger
           cdxWriter.process(files, true); //True means full WARC-file path will be written.
        return stringWriter.toString();               
        }
    }
    
}
