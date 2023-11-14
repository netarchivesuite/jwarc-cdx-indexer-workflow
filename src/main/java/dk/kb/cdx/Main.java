package dk.kb.cdx;

import dk.kb.cdx.config.ServiceConfig;
import dk.kb.cdx.workflow.CdxIndexerWorkflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
        
    private static final Logger log = LoggerFactory.getLogger(Main.class);
        
    /**
     * Start the workflow. All parameters will be taken from the YAML file.  
     * Will give exception if arguments are given to the main method. 
     * 
     * @param args No arguments. Workflow parameters are taken from the YAML file.
     * @throws Exception
     */
    public static void main(String... args) throws Exception {
        if (args.length != 0) {
        	throw new Exception("Main method must not be called with arguments. All values for workflow are taken from the YAML configuration.");
        }
    	
    	BuildInfoManager.logApplicationInfo(); // Mandated by Operations
        ServiceConfig.initialize(System.getProperty("dk.kb.applicationConfig"));
  
        try {            
           CdxIndexerWorkflow.main(ServiceConfig.CDX_SERVER_URL, ServiceConfig.WARCS_INPUT_LIST_FILE, ServiceConfig.WARCS_OUTPUT_LIST_FILE, ""+ServiceConfig.USEABSOLUTEPATHS,""+ServiceConfig.THREADS, ServiceConfig.IGNORE_PATTERN,""+ServiceConfig.DRYRUN);
        } catch(Exception e) { //Will only happen if workers can not be started
            log.error("Error starting workers. Job terminated");
            System.err.println("Error starting workers. Job terminated"); 
            e.printStackTrace();
            System.exit(1);
        }

        System.exit(0);
    }    
   
}