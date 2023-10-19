package dk.kb.cdx;

import dk.kb.util.Resolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

/**
 * Loads the property file 'build.properties' which has been populated with application
 * name, version and build time.
 */
public class BuildInfoManager {
    private static final String BUILD_PROPERTY_FILE = "build.properties";

    private static final Logger log = LoggerFactory.getLogger(BuildInfoManager.class);

    private static String name = null;
    private static String version = null;
    private static String buildTime = null;

    /**
     * @return the human readable name of the application, as defined in pom.xml.
     */
    public static String getName() {
        if (name == null) {
            loadBuildInfo();
        }
        return name;
    }

    public static String getPackageName() {
        String name = BuildInfoManager.class.getPackage().getName();

        return name.substring(0, name.lastIndexOf('.'));
    }

    /**
     * @return the version of the application, as defined in pom.xml.
     */
    public static String getVersion() {
        if (version == null) {
            loadBuildInfo();
        }

        return version;
    }

    /**
     * @return the build time of the application.
     */
    public static String getBuildTime() {
        if (buildTime == null) {
            loadBuildInfo();
        }
        return buildTime;
    }

    /**
     * Load build information from {@link #BUILD_PROPERTY_FILE} on the path.
     */
    private static synchronized void loadBuildInfo() {
        if (name != null) { // Already resolved
            return;
        }

        Properties properties = new Properties();
        try (InputStream is = Resolver.resolveStream(BUILD_PROPERTY_FILE)) {
            if (is == null) {
                log.warn("Unable to load '" + BUILD_PROPERTY_FILE + "' from the classpath. " +
                         "Build information will be unavailable");
            } else {
                properties.load(is);
            }
        } catch (IOException e) {
            log.warn("Could not load build information from:" + BUILD_PROPERTY_FILE, e);
        }

        name = properties.getProperty("APPLICATION.NAME", "unknown");
        version = properties.getProperty("APPLICATION.VERSION", "unknown");
        buildTime = properties.getProperty("APPLICATION.BUILDTIME", "unknown");
    }

    /**
     * Logs application-ID & version, JVM version, memory allocation etc.
     */
    public static void logApplicationInfo() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "<unresolvable>";
        }
        log.info("Initializing service {} {} build {}, running under Java {} with Xmx={}MB on machine {}",
                 getName(), getVersion(), getBuildTime(),
                 System.getProperty("java.version"), Runtime.getRuntime().maxMemory()/1048576,
                 hostname);
    }
}
