# File to be sourced in to application runner "scripts/start-script.sh"
# Allow for customisations here (allows application runner to be effectively read-only)

MAIN_CLASS=dk.kb.cdx.Main
APP_CONFIG=jwarc-cdx-indexer-workflow-*.yaml

#Optional parameter to override default JAVA_OPTS
JAVA_OPTS="-Xmx8192m -Xms8192m"

#Optional parameter to override default classpath (lib folder)
#CLASS_PATH_OVERRIDE="/my/other/lib/dir/*:$SCRIPT_DIR/../lib/*"


