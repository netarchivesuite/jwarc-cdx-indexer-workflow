#!/bin/bash

# The CDX-workflow java repository is found here: https://github.com/netarchivesuite/jwarc-cdx-indexer-workflow/tree/main/src/main/java/dk/kb/cdx/workflow

# The script will find files in /netarkive mount that are 2 days old not counting today and will write them to a text file. ie: 2026-03-27_2026-03-29.txt
# The java workflow is then given the input-file and other parameters such as URL to the CDX-server.  For each warc-fil in the list, the workflow will index it into OutbackCdx.
# When files are completed they will be written to same filename with .COMPLETED appended.
# The java workflow will append log into the WORKFLOW_APPEND_LOG configured file.
# With the 2 days configured, the crontab job must also be started every second day. 

# Calculate dates
TWO_DAYS_AGO=$(date -d "2 days ago" +%Y-%m-%d)
TODAY=$(date +%Y-%m-%d)

#TEG LOCAL TEST
#WARC_FOLDER="/home/teg/temp/ll"
#OUTPUT_FOLDER="/home/teg/temp/logs"

WARC_FOLDER="/netarkivet"
OUTPUT_FOLDER="/netarkiv-cdx/logs/"

# Output filename (only the two dates, clean)
OUTPUT_FILE="${OUTPUT_FOLDER}/${TWO_DAYS_AGO}_${TODAY}.txt"

#CDX-workflow variables
#WORKFLOW_JAR="/home/teg/temp/jwarc-cdx-indexer-workflow-1.1-jar-with-dependencies.jar"
WORKFLOW_JAR="/netarkiv-cdx/cdx-index-workflow/jwarc-cdx-indexer-workflow-1.1-jar-with-dependencies.jar"

WORKFLOW_MAIN_CLASS="dk.kb.cdx.workflow.CdxIndexerWorkflow"
WORKFLOW_THREADS=8
WORKFLOW_CDX_URL="http://netarkivet-cdx-02p.bitarkiv.kb.dk:8081/index?badLines=skip"
OUTPUT_FILE_COMPLETED="${OUTPUT_FILE}.COMPLETED"
WORKFLOW_APPEND_LOG="cdx_indexer_workflow.log"
METADATA_IGNORE="metadata"
DRY_RUN="false"
ABSOLUTE_PATH="true"

# Start timing the find command
START_TIME=$(date +%s)

echo "Starting locating new warc-files and will write new files to: ${OUTPUT_FILE}"

# Reliable way to get files from 2 days ago (inclusive) up to yesterday (inclusive).This can take 1-2 hours, so good enough for now(we have 7M warc-files total).
echo "find command: find ${WARC_FOLDER} -type f  -newermt "${TWO_DAYS_AGO}" ! -newermt "${TODAY}""


find ${WARC_FOLDER} -type f \
    -newermt "${TWO_DAYS_AGO}" \
    ! -newermt "${TODAY}" \
    > "${OUTPUT_FILE}"

# End timing
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# Calculate human-readable time
HOURS=$((DURATION / 3600))
MINUTES=$(((DURATION % 3600) / 60))
SECONDS=$((DURATION % 60))

# Count files
FILE_COUNT=$(wc -l < "${OUTPUT_FILE}" | awk '{print $1}')

echo "   Find command completed in ${HOURS}h ${MINUTES}m ${SECONDS}s"
echo "   Files found: ${FILE_COUNT}"
echo "   Output saved to: ${OUTPUT_FILE}"

# Quick verification (optional)
if [ "$FILE_COUNT" -gt 0 ]; then
    echo "   First file: $(head -n 1 "${OUTPUT_FILE}")"
    echo "   Last file:  $(tail -n 1 "${OUTPUT_FILE}")"
fi

# Example of posting a file containing  warc-files names to the CDX-indexer workflow
# java -Xmx16g -cp jwarc-cdx-indexer-workflow-1.1-jar-with-dependencies.jar dk.kb.cdx.workflow.CdxIndexerWorkflow http://netarkivet-cdx-02p.bitarkiv.kb.dk:8081/index?badLines=skip /home/teg/temp/logs/text.txt  /home/teg/temp/logs/text.txt.COMPLETED true 8 metadata false 2>&1 >> cdx_indexer_workflow_warcs.20250501_to_20251217.log  


#Start the workflow

echo "Starting java workflow with command:"
echo "java -Xmx16g -cp ${WORKFLOW_JAR} ${WORKFLOW_MAIN_CLASS} ${WORKFLOW_CDX_URL} ${OUTPUT_FILE} ${OUTPUT_FILE_COMPLETED} ${ABSOLUTE_PATH} ${WORKFLOW_THREADS} ${METADATA_IGNORE} ${DRY_RUN} 2>&1 >> ${WORKFLOW_APPEND_LOG}"
java  -Xmx16g -cp ${WORKFLOW_JAR} ${WORKFLOW_MAIN_CLASS} ${WORKFLOW_CDX_URL} ${OUTPUT_FILE} ${OUTPUT_FILE_COMPLETED} ${ABSOLUTE_PATH} ${WORKFLOW_THREADS} ${METADATA_IGNORE} ${DRY_RUN} 2>&1 >> ${WORKFLOW_APPEND_LOG}

echo "Job finished at $(date)"


