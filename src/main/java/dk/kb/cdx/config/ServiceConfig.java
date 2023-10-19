package dk.kb.cdx.config;

import dk.kb.cdx.Main;
import dk.kb.util.yaml.YAML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sample configuration class using the Singleton and Observer patterns.
 * See {@link {@url https://en.wikipedia.org/wiki/Observer_pattern}}
 *
 * If wanted, changes to the configuration source (typically files) can result in an update of the ServiceConfig and
 * a callback to relevant classes. To enable this, add autoupdate keys to the YAML config:
 * <pre>
 * config:
 *   autoupdate:
 *     enabled: true
 *     intervalms: 60000
 * </pre>
 * Notifications on config changes can be received using {@link #registerObserver(Observer)}.
 *
 * Alternatively {@link #AUTO_UPDATE_DEFAULT} and {@link #AUTO_UPDATE_MS_DEFAULT} can be set so that auto-update is
 * enabled by default for the application.
 *
 * Implementation note: Watching for changes is a busy-wait, i.e. the ServiceConfig actively reloads the configuration
 * each {@link #autoUpdateMS} milliseconds and checks if is has changed. This is necessary as the source for the
 * configuration is not guaranteed to be a file (it could be a URL or packed in a WAR instead), so watching for file
 * system changes is not solid enough. This also means that the check does have a non-trivial overhead so setting the
 * autoupdate interval to less than a minute is not recommended.
 */
public class ServiceConfig {
    private static final Logger log = LoggerFactory.getLogger(ServiceConfig.class);

    private static final Set<Observer> observers = new HashSet<>();

    private static final String AUTO_UPDATE_KEY = ".config.autoupdate.enabled";
    private static final boolean AUTO_UPDATE_DEFAULT = false;
    private static final String AUTO_UPDATE_MS_KEY = ".config.autoupdate.intervalms";
    private static final long AUTO_UPDATE_MS_DEFAULT = 60*1000; // every minute

    private static boolean autoUpdate = AUTO_UPDATE_DEFAULT;
    private static long autoUpdateMS = AUTO_UPDATE_MS_DEFAULT;
    private static AutoUpdater autoUpdater = null;

    private static String configSource = null;

    private static int THREADS=1;
    private static String CDX_SERVER_URL=null;
    private static String WARCS_INPUT_LIST_FILE=null;
    private static String WARCS_OUTPUT_LIST_FILE=null;
    
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
        
        String cdx_server=serviceConfig.getString("config.workflow.cdx_server_url");
        String input_file=serviceConfig.getString("config.workflow.input_file");
        String output_file=serviceConfig.getString("config.workflow.output_file");
        int threads=serviceConfig.getInteger("config.workflow.threads");
        
        log.info("Load serviceconfig with properties:" );
        log.info("Cdx server url:"+cdx_server );
        log.info("WARC input file list:"+input_file );
        log.info("WARC output file list:"+output_file );
        log.info("Number of workers:"+threads);
    }

    private static void assignConfig(YAML conf) {
        log.debug("Assigning config with {} observers and autoUpdate={}",
                  observers.size(), conf.getBoolean(AUTO_UPDATE_KEY, AUTO_UPDATE_DEFAULT));
        conf.setExtrapolate(true); // Enable system properties expansions, such as '${user.home}'
        serviceConfig = conf;
        notifyObservers();
        setupAutoConfig();
    }

    private static synchronized void setupAutoConfig() {
        autoUpdate = serviceConfig.getBoolean(AUTO_UPDATE_KEY, AUTO_UPDATE_DEFAULT);
        autoUpdateMS = serviceConfig.getLong(AUTO_UPDATE_MS_KEY, AUTO_UPDATE_MS_DEFAULT);

        if (autoUpdater != null) {
            autoUpdater.shutdown();
        }
        if (!autoUpdate) {
            return;
        }

        autoUpdater = new AutoUpdater(configSource, autoUpdateMS);
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

    /**
     * Shuts down the ServiceConfig, which in practise means shutting down auto-updating of the configuration.
     * The ServiceConfig should not be used after this has been called.
     */
    public static void shutdown() {
        log.info("Shutting down");
        if (autoUpdater != null) {
            autoUpdater.shutdown();
        }
    }

    

    /* -------------------------------------------------------------------------------------------------------------- */

    /**
     * Register an observer of configuration changes.
     * If the configuration has already been loaded, which is the default case, the observer is notified immediately.
     *
     * Reloading of the configuration is project dependent and must be enabled for the observer to be notified on
     * subsequent changes to the configuration.
     * Call {@link #isAutoUpdating()} to determine whether the configuration might be updated post-initialization.
     * @param observer called upon registration if a config exists, and if the configuration changes.
     */
    public static synchronized void registerObserver(Observer observer) {
        log.debug("Registering configuration update observer {}", observer);
        observers.add(observer);
        if (serviceConfig != null) {
            observer.setConfig(serviceConfig);
        }
    }

    /**
     * Unregisters a previously registered configuration change observer.
     * @param observer an observer previously added with {@link #registerObserver(Observer)}.
     * @return true if the observer was previously registered, else false.
     */
    public static synchronized boolean unregisterObserver(Observer observer) {
        boolean observerWasRegistered = observers.remove(observer);
        log.debug(observerWasRegistered ?
                          "Unregistered configuration update observer {}" :
                          "Attempted to unregister configuration update observer {} but it was not found",
                  observer);
        return observerWasRegistered;
    }

    private static void notifyObservers() {
        observers.forEach(o -> o.setConfig(serviceConfig));
    }

    /**
     * Functional equivalent of {@code Consumer<YAML>} with a less generic method name, to support registering observers
     * with {@code registerObserver(this)} instead of {@code registerObserver(this::setConfig}.
     */
    @FunctionalInterface
    public interface Observer {
        void setConfig(YAML config);
    }

    /* -------------------------------------------------------------------------------------------------------------- */

    /**
     * @return true if the configuration is automatically reloaded if the source files are changed.
     */
    public static boolean isAutoUpdating() {
        return autoUpdate;
    }

    /**
     * Checks for changes of the underlying configuration sources and triggers application configuration changes.
     */
    private static class AutoUpdater extends Thread {
        private boolean shutdown = false;

        private final String configSource;
        private final long intervalMS;
        private int noSleeps = 0;

        private long lastCheck = -1;

        public AutoUpdater(String configSource, long intervalMS) {
            super("ConfigUpdate_" + System.currentTimeMillis());
            this.configSource = configSource;
            this.intervalMS = intervalMS;
            this.setDaemon(true);
            log.info("Starting config watcher with {} ms interval", intervalMS);
            this.start();
        }

        @SuppressWarnings("BusyWait")
        @Override
        public void run() {
            while (!shutdown) {
                checkForChange();
                try {
                    long timeToWait = (lastCheck + intervalMS) - System.currentTimeMillis();
                    if (timeToWait > 0) {
                        Thread.sleep(timeToWait);
                    } else {
                        if (noSleeps++ < 10) {
                            log.warn("AutoUpdate: No sleep before next check for configuration change. " +
                                     "The interval of {} ms should probably be longer. " +
                                     "This message is muted after 10 occurrences for the current config", intervalMS);
                        }
                    }
                } catch (InterruptedException e) {
                    // Do nothing as the while-check handles interruptions
                }
            }
            log.debug("Stopping config watcher as shutdown has been called");
        }

        private void checkForChange() {
            lastCheck = System.currentTimeMillis();
            log.debug("AutoUpdate: Loading YAML from config source '{}'", configSource);
            YAML candidate;
            try {
                candidate = YAML.resolveLayeredConfigs(configSource);
            } catch (IOException e) {
                log.warn("AutoUpdate: Exception while loading config", e);
                return;
            }

            if (candidate == null) {
                log.warn("AutoUpdate: Got null when loading from source config '{}'", configSource);
                return;
            }

            if (serviceConfig == null || !candidate.toString().equals(serviceConfig.toString())) {
                log.debug("AutoUpdate: Detected configuration change, triggering update");
                assignConfig(candidate);
            }
        }

        /**
         * Stop the auto updater.
         */
        public void shutdown() {
            shutdown = true;
            this.interrupt();
        }
    }

}
