package dk.kb.cdx.config;

import dk.kb.cdx.Main;
import dk.kb.util.yaml.YAML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ServiceConfig {
    private static final Logger log = LoggerFactory.getLogger(ServiceConfig.class);
    private static String configSource = null;

    public static int THREADS=1;
    public static String CDX_SERVER_URL=null;
    public static String WARCS_INPUT_LIST_FILE=null;
    public static String WARCS_OUTPUT_LIST_FILE=null;
    public static boolean USEABSOLUTEPATHS = false;
    public static boolean DRYRUN=false;
    
    /*
    cdx_server_url: http://localhost:8081/index?badLines=skip
        input_file: /netarkiv-cdx/netarkivet.files.20230705
        output_file:  /netarkiv-cdx/netarkivet.files.20230705.COMPLETED.txt
        threads: 24
    */
    
    /**
     * Besides parsing of YAML files using SnakeYAML, the YAML helper class provides convenience
     * methods like {@code getInteger("someKey", defaultValue)} and {@code getSubMap("config.sub1.sub2")}.
     */
    private static YAML serviceConfig;

    /**
     * Initialized the configuration from the provided configFile.
     * This should normally be called from {@link Main} aspart of startup.
     * @param configSource the configuration to load.
     * @throws IOException if the configuration could not be loaded or parsed.
     */
    public static synchronized void initialize(String configSource) throws IOException {
        ServiceConfig.configSource = configSource;
        assignConfig(YAML.resolveLayeredConfigs(configSource));
        
        CDX_SERVER_URL=serviceConfig.getString("config.workflow.cdx_server_url");
        WARCS_INPUT_LIST_FILE=serviceConfig.getString("config.workflow.input_file");
        WARCS_OUTPUT_LIST_FILE=serviceConfig.getString("config.workflow.output_file");       
        THREADS=serviceConfig.getInteger("config.workflow.threads");
        DRYRUN=serviceConfig.getBoolean("config.dry_run");
        USEABSOLUTEPATHS=serviceConfig.getBoolean("use_absolute_paths");
        log.info("Load serviceconfig with properties:" );
        log.info("Cdx server url:"+ CDX_SERVER_URL );
        log.info("WARC input file list:"+WARCS_INPUT_LIST_FILE );
        log.info("WARC output file list:"+WARCS_OUTPUT_LIST_FILE );
        log.info("Use absolute paths:"+USEABSOLUTEPATHS );
        log.info("Number of workers:"+THREADS);
        log.info("Dryrun:"+DRYRUN);
    }

    private static void assignConfig(YAML conf) {
        conf.setExtrapolate(true); // Enable system properties expansions, such as '${user.home}'
        serviceConfig = conf;
    }

    /**
     * Direct access to the backing YAML-class is used for configurations with more flexible content
     * and/or if the service developer prefers key-based property access.
     * @see #getHelloLines() for alternative.
     * @return the backing YAML-handler for the configuration.
     */
    public static YAML getConfig() {
        if (serviceConfig == null) {
            throw new IllegalStateException("The configuration should have been loaded, but was not");
        }
        return serviceConfig;
    }

    

    



}
