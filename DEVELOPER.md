# Developer documentation

This project is built from the [stand-alone-java-template](https://sbprojects.statsbiblioteket.dk/stash/projects/ARK/repos/stand-alone-java-template/browse)
from the Royal Danish Library.

The information in this document is aimed at developers that are not proficient in the java webapp template, Jetty, 
Tomcat deployment or OpenAPI.


## Basic

Build with standard
```
mvn package
```

This produces `target/jwarc-cdx-indexer-workflow-1.0-SNAPSHOT-distribution.tar.gz` which contains JARs, configurations and
`start-script.sh` for running the application. 

Quick development testing can be done by calling
```shell
target/jwarc-cdx-indexer-workflow-*-distribution/jwarc-cdx-indexer-workflow-*/bin/start-script.sh
```

## Configuration

Configuration of the project is handled with [YAML](https://en.wikipedia.org/wiki/YAML). It is split into multiple parts:
 
 * `behaviour` which contains setup for thread pools, limits for arguments etc. This is controlled by the developers.
 * `environment` which contains server names, userIDs, passwords etc. This is controlled by operations.
 * `local` which contains temporary developer overrides. This is controlled by the individual developer.

During development the configurations are located in the `conf`-folder as `src/main/conf/jwarc-cdx-indexer-workflow-behaviour.yaml`  
and `src/main/conf/jwarc-cdx-indexer-workflow-local.yaml`. When the project is started using Jetty, these are the configurations that are used.

The file `src/main/conf/jwarc-cdx-indexer-workflow-environment.yaml.sample` is not active during development and only acts as a 
template for production deployment. The relevant development properties should be specified in
`src/main/conf/jwarc-cdx-indexer-workflow-local.yaml`.

Access to the configuration is through the static class at `src/main/java/dk/kb/cdx/config/ServiceConfig.java`.

**Note**: The environment configuration typically contains sensitive information. Do not put it in open code
repositories. To guard against this, `src/main/conf/jwarc-cdx-indexer-workflow-environment.yaml` is added to `.gitignore`. 


## Tests

Unit tests are run using the surefire plugin (configured in the parent pom).

If you have unit tests that takes long to run, and don't want them to run when at every invocation of mvn package,
annotate the testcase with `@Tag("slow")` in the java code. 
To run all unit tests including the ones tagged as slow, enable the `allTests` maven profile: e.g. `mvn clean package -PallTests`.


## Changelog

The changelog follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) guidelines.
The trickiest part is to get the diff-links correct at the bottom of CHANGELOG.md as the git *tags* used for references
should be created *after* the CHANGELOG has been updated.

For GitHub-projects, the syntax is
```
[Unreleased](https://github.com/kb-dk/jwarc-cdx-indexer-workflow/compare/v1.0.0...HEAD)
[1.1.0](https://github.com/kb-dk/jwarc-cdx-indexer-workflow/compare/v1.0.0...v1.1.0)
[1.0.0](https://github.com/kb-dk/jwarc-cdx-indexer-workflow/releases/tag/v1.0.0)
```

For BitBucket (KB's internal git), the syntax is
```
[Unreleased](https://sbprojects.statsbiblioteket.dk/stash/projects/ARK/repos/jwarc-cdx-indexer-workflow/compare/commits?targetBranch=refs%2Ftags%2Fv1.1.0&sourceBranch=refs%2Fheads%2Fmaster)
[1.1.0](https://sbprojects.statsbiblioteket.dk/stash/projects/ARK/repos/jwarc-cdx-indexer-workflow/compare/commits?targetBranch=refs%2Ftags%2Fv1.0.0&sourceBranch=refs%2Ftags%2Fv1.1.0)
[1.0.0](https://sbprojects.statsbiblioteket.dk/stash/projects/ARK/repos/jwarc-cdx-indexer-workflow/commits?until=refs%2Ftags%2Fv1.0.0)
```
Note that both the `ARK`-part and the repo-id `jwarc-cdx-indexer-workflow` is project-specific.


## Release procedure

1. Review that the `version` in `pom.xml` is fitting. `jwarc-cdx-indexer-workflow` uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html): The typical release
will bump the `MINOR` version and set `PATCH` to 0. Keep the `-SNAPSHOT`-part as
the Maven release plugin handles that detail.   
1. Ensure that [CHANGELOG.md](CHANGELOG.md) is up to date. `git log` is your friend. 
Ensure that the about-to-be-released version is noted in the changelog entry
1. Ensure all local changes are committed and pushed.
1. Ensure that your local `.m2/settings.xml` has a current `sbforge-nexus`-setup
(contact Kim Christensen @kb or another Maven-wrangler for help)
1. Follow the instructions on
[Guide to using the release plugin](https://maven.apache.org/guides/mini/guide-releasing.html)
which boils down to
   * Run `mvn clean release:prepare`
   * Check that everything went well, then run `mvn clean release:perform`
   * Run `git push`   
   If anything goes wrong during release, rollback and delete tags using something like
   `mvn release:rollback ; git tag -d jwarc-cdx-indexer-workflow-1.4.x ; git push --delete origin jwarc-cdx-indexer-workflow-1.4.x`
