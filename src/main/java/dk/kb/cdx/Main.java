package dk.kb.cdx;

import java.util.Arrays;
import java.util.concurrent.Callable;


import dk.kb.cdx.config.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
/*
 *  setsid nohup java -Xmx16g -cp jwarc_workflow.jar  org.netpreserve.jwarc.workflows.CdxIndexerWorkflow 24 http://netarkivet-cdx-01p:8081/index?badLines=skip /netarkiv-cdx/netarkivet.files.20230705  /netarkiv-cdx/netarkivet.files.20230705.COMPLETED.txt 2>&1 >> cdx_indexer_workflow.log


 * 
 */


//Check link below for examples.
//https://picocli.info/#_introduction
@CommandLine.Command()
public class Main implements Callable<Integer>{
        
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    
    /*
     * Implement the normal 'main' method here
     */
    @Override
    public Integer call() throws Exception {        
   
     // How to load a property
     //When debugging from IDEA, add -Ddk.kb.applicationConfig="src/main/conf/cdx-*.yaml" to "VM options"
        ServiceConfig.initialize(System.getProperty("dk.kb.applicationConfig"));

                        
        log.info("job ended");
        System.out.println("Job ended");                        
        return 0; //Exit code
    }
    
    
    public static void main(String... args) {
        BuildInfoManager.logApplicationInfo(); // Mandated by Operations
        System.out.println("Arguments passed by commandline is: " + Arrays.asList(args));
        CommandLine app = new CommandLine(new Main());
        int exitCode = app.execute(args);
        SystemControl.exit(exitCode);
    }

    /**
     * Handles communication with the calling environment. Currently only in the form of sending the proper exit code.
     */
    static class SystemControl {
        static void exit(int exitCode) {
            if (exitCode == 0) {
                log.info("Exiting with code 0 (success)");
            } else {
                log.error("Exiting with code " + exitCode + " (fail)");
            }
            System.exit(exitCode);
        }
    }
}