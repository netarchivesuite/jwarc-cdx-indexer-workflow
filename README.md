# jwarc-cdx-indexer-workflow


Developed and maintained by the Royal Danish Library.

The workflow will use the 4 settings for the CDX-indexer automatic.  (CDX11 format, digest-unchanged, post-append, include-revisits)
 
 
## Workflow arguments
The workflow takes 6 arguments
1) URL to CDX-server
2) Text file will list of WARC-files to index. (Full filepath, one WARC file on each line)
3) Text file to output completed WARC-files. File will be created if it does not exist
4) Absolute path for WARC-files in CDX-server
5) Number of threads to use for the workflow.
6) Dry run. If try will not post CDX-data to CDX-server. Use for test mode


If the indexing workflow is interrupted and stopped, it can just be restarted with the same input WARC-file. It will skip all WARC-files that are listed in the output completed file.
If the CDX server does not return a http status. (no connection, server dead etc), then the thread will terminate and log this event. This is to avoid 'processing' and mark then completed when they will fail. 
Some WARC-files will return HTTP error status from the CDX-server, but this is expected and due to corrupt WARC-files. This is mostly old ARC files with http-header errors.


## Starting the workflow.
Configure the yaml property file with the 6 properties
 Arguments:
 * cdx_server_url: The outback CDX server require the parameter 'badLines=skip' or it will terminate on invalid http headers.
 * input_file: A text file where each line is the full path to a WARC-file 
 * output_file: Completed WARC-files will be written to this file. It will be created if it does not exist. A new workflow will skip files already in the completed list
 * use_absolute_paths: Will store the full path of the WARC-file in the CDX server. This will remove the need for a lookup service in PyWb.
 * threads: Do not increase number of threads over 48 since the Outback CDX server also must be able to handle the load. IO when reading is often the * bottleneck here, going over 24 may not help.
 * dry_run: If true no data will be sent to the CDX-server. Use to test the setup before. Remember to delete the output file before starting a real run.


## Call the start script:
bin/start-script.sh


## Create a input file with WARC files to process
To include all files recursive under a folder with absolute path '/home/user/warcs' use: 
* find /home/user/warcs -type f > warc.files.txt


## Implementation details:
The list of WARC files to process is read from the input file and stored in List<String>.
The list of WARC files completed is stored in the output file file stored in HashSet<String> so the contains method is fast.

A synchronized method 'getNextWarcFile' will return next file to process when a thread require a new file.
If the file is already in the completed set it will just skip returning it and instead try same check for the next file.
When a WARC file has been completed it will be written to the output file and also add to the memory Set of completed files.

Since the job will take months to complete, regular check not too many threads has been stopped with:
less cdx_indexer_workflow.log | grep 'Stopping thread'
A thread will stop if the response from the CDX-server is not expected.
So far it has never happened unless when forced by stopping the CDX-server for testing.
 
Expected response from CDX-server: Added 179960 records


## Requirements

* Maven 3                                  
* Java 11/17

## Setup

 * Outback CDX server installed and running. See https://github.com/nla/outbackcdx
 * Optional PyWB to see the playback. (Require Python virtual enviroment). 


## Build & run

Build with standard
```
mvn package
```

This produces `target/jwarc-cdx-indexer-workflow-<version>-distribution.tar.gz` which contains JARs, configurations and
`start-script.sh` for running the application. 

Quick development testing can be done by calling
```shell
target/jwarc-cdx-indexer-workflow-*-SNAPSHOT-distribution/jwarc-cdx-indexer-workflow-*-SNAPSHOT/bin/start-script.sh
```


