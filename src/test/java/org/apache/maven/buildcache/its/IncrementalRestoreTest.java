/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.buildcache.its;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

/**
 * Check if a restoration restores build incrementally,i.e. package -> verify -> install -> deploy,
 * so that the cached executions are not run again for builds with a higher goal.
 */
@IntegrationTest("src/test/projects/mbuildcache-incremental")
public class IncrementalRestoreTest {

    public static final String SAVED_BUILD_TO_LOCAL_FILE = "Saved Build to local file: ";
    public static final String GENERATED_JAR = "target/mbuildcache-incremental-final.jar";
    public static final String GENERATED_SOURCES_JAR = "target/mbuildcache-incremental-final-sources.jar";
    public static final String GENERATED_JAVADOC_JAR = "target/mbuildcache-incremental-final-javadoc.jar";

    public static final String EXTRA_OUTPUT_1 =
            "target" + File.separatorChar + "extra-resources" + File.separatorChar + "extra-readme-1.md";
    public static final String EXTRA_OUTPUT_2 = "target/extra-resources/extra-readme-2.md";
    public static final String EXTRA_OUTPUT_3 = "target/extra-resources/other-readme-1.md";
    public static final String EXTRA_OUTPUT_4 = "target/other-resources/extra-readme-1.md";
    public static final String EXTRA_OUTPUT_5 = "target/other-resources/extra-readme-2.md";
    public static final String EXTRA_OUTPUT_6 = "target/other-resources/other-readme-1.md";
    public static final String SKIPPING_PLUGIN_EXECUTION_CACHED_RESOURCES_RESOURCES =
            "Skipping plugin execution (cached): resources:resources";
    public static final String SKIPPING_PLUGIN_EXECUTION_CACHED_COMPILER_COMPILE =
            "Skipping plugin execution (cached): compiler:compile";
    public static final String SKIPPING_PLUGIN_EXECUTION_CACHED_RESOURCES_TEST_RESOURCES =
            "Skipping plugin execution (cached): resources:testResources";
    public static final String SKIPPING_PLUGIN_EXECUTION_CACHED_COMPILER_TEST_COMPILE =
            "Skipping plugin execution (cached): compiler:testCompile";
    public static final String SKIPPING_PLUGIN_EXECUTION_CACHED_SUREFIRE_TEST =
            "Skipping plugin execution (cached): surefire:test";
    public static final String SKIPPING_PLUGIN_EXECUTION_CACHED_JAR_JAR = "Skipping plugin execution (cached): jar:jar";
    public static final String SKIPPING_PLUGIN_EXECUTION_CACHED_RESOURCES_COPY =
            "Skipping plugin execution (cached): resources:copy-resources";
    public static final String INSTALL_DEFAULT_INSTALL_MBUILDCACHE_INCREMENTAL =
            "install (default-install) @ mbuildcache-incremental";
    public static final String SKIPPING_PLUGIN_EXECUTION_CACHED_INSTALL_INSTALL =
            "Skipping plugin execution (cached): install:install";
    public static final String DEPLOY_DEFAULT_DEPLOY_MBUILDCACHE_INCREMENTAL =
            "deploy (default-deploy) @ mbuildcache-incremental";
    public static final String LOCAL_BUILD_WAS_NOT_FOUND_BY_CHECKSUM = "Local build was not found by checksum";
    public static final String RESOURCES_DEFAULT_RESOURCES_MBUILDCACHE_INCREMENTAL =
            "resources (default-resources) @ mbuildcache-incremental";
    public static final String COMPILE_DEFAULT_COMPILE_MBUILDCACHE_INCREMENTAL =
            "compile (default-compile) @ mbuildcache-incremental";
    public static final String TEST_RESOURCES_DEFAULT_TEST_RESOURCES_MBUILDCACHE_INCREMENTAL =
            "testResources (default-testResources) @ mbuildcache-incremental";
    public static final String TEST_COMPILE_DEFAULT_TEST_COMPILE_MBUILDCACHE_INCREMENTAL =
            "testCompile (default-testCompile) @ mbuildcache-incremental";
    public static final String TEST_DEFAULT_TEST_MBUILDCACHE_INCREMENTAL =
            "test (default-test) @ mbuildcache-incremental";
    public static final String JAR_DEFAULT_JAR_MBUILDCACHE_INCREMENTAL = "jar (default-jar) @ mbuildcache-incremental";
    public static final String
            FOUND_CACHED_BUILD_RESTORING_ORG_APACHE_MAVEN_CACHING_TEST_MBUILDCACHE_INCREMENTAL_FROM_CACHE_BY_CHECKSUM =
                    "Found cached build, restoring org.apache.maven.caching.test:mbuildcache-incremental from cache by checksum";
    public static final String MBUILDCACHE_INCREMENTAL_JAR = "mbuildcache-incremental.jar";
    public static final String MBUILDCACHE_INCREMENTAL_SOURCES_JAR = "mbuildcache-incremental-sources.jar";
    public static final String MBUILDCACHE_INCREMENTAL_JAVADOC_JAR = "mbuildcache-incremental-javadoc.jar";
    public static final String INTEGRATION_TEST_DEFAULT_MBUILDCACHE_INCREMENTAL =
            "integration-test (default) @ mbuildcache-incremental";
    public static final String VERIFY_DEFAULT_MBUILDCACHE_INCREMENTAL = "verify (default) @ mbuildcache-incremental";
    public static final String SKIPPING_PLUGIN_EXECUTION_CACHED_FAILSAFE_INTEGRATION_TEST =
            "Skipping plugin execution (cached): failsafe:integration-test";
    public static final String SKIPPING_PLUGIN_EXECUTION_CACHED_FAILSAFE_VERIFY =
            "Skipping plugin execution (cached): failsafe:verify";

    private Path jarCacheFile;
    private Path jarSourcesCacheFile;
    private Path jarJavadocCacheFile;

    @Test
    void simple(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);
        verifier.setMavenDebug(true);

        initialBuild(verifier);
        verifyPackageWithCache(verifier);
        verifyWithCache(verifier);
        installWithCache(verifier);
        deployWithCache(verifier);
        replayInstallWithCache(verifier);
    }

    private void initialBuild(Verifier verifier) throws VerificationException, IOException {
        // First build, nothing in cache
        verifier.setLogFileName("../log-package.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(LOCAL_BUILD_WAS_NOT_FOUND_BY_CHECKSUM);
        verifier.verifyTextInLog(RESOURCES_DEFAULT_RESOURCES_MBUILDCACHE_INCREMENTAL);
        verifier.verifyTextInLog(COMPILE_DEFAULT_COMPILE_MBUILDCACHE_INCREMENTAL);
        verifier.verifyTextInLog(TEST_RESOURCES_DEFAULT_TEST_RESOURCES_MBUILDCACHE_INCREMENTAL);
        verifier.verifyTextInLog(TEST_COMPILE_DEFAULT_TEST_COMPILE_MBUILDCACHE_INCREMENTAL);
        verifier.verifyTextInLog(TEST_DEFAULT_TEST_MBUILDCACHE_INCREMENTAL);
        verifier.verifyTextInLog(JAR_DEFAULT_JAR_MBUILDCACHE_INCREMENTAL);
        verifier.verifyTextInLog(SAVED_BUILD_TO_LOCAL_FILE);
        verifier.verifyFilePresent(GENERATED_JAR);

        // First build : all resources are present in the target folder
        verifyAllExtraOutputsPresent(verifier);

        Path buildInfoPath = getSavedBuildInfoPath(verifier);
        jarCacheFile = buildInfoPath.getParent().resolve(MBUILDCACHE_INCREMENTAL_JAR);
        jarSourcesCacheFile = buildInfoPath.getParent().resolve(MBUILDCACHE_INCREMENTAL_SOURCES_JAR);
        jarJavadocCacheFile = buildInfoPath.getParent().resolve(MBUILDCACHE_INCREMENTAL_JAVADOC_JAR);
        Assertions.assertTrue(Files.exists(jarCacheFile), "Expected artifact saved in build cache.");
        Assertions.assertFalse(
                Files.exists(jarSourcesCacheFile), "Not expected sources artifact saved in build cache.");
        Assertions.assertFalse(
                Files.exists(jarJavadocCacheFile), "Not expected javadoc artifact saved in build cache.");
    }

    private void verifyPackageWithCache(Verifier verifier) throws VerificationException {
        // Verify clean build, with the same goal should be fully restored
        cleanBuild(verifier);

        verifier.setLogFileName("../log-package-2.txt");
        verifier.executeGoal("package");
        verifier.verifyTextInLog(
                FOUND_CACHED_BUILD_RESTORING_ORG_APACHE_MAVEN_CACHING_TEST_MBUILDCACHE_INCREMENTAL_FROM_CACHE_BY_CHECKSUM);
        verifier.verifyTextInLog(
                "Found cached build, restoring org.apache.maven.caching.test:mbuildcache-incremental from cache by checksum");
        verifier.verifyErrorFreeLog();
        verifySkippedPluginExecutions(verifier);
        verifier.verifyFilePresent(GENERATED_JAR);
        verifyCachedExtraOutputs(verifier);
        Assertions.assertTrue(Files.exists(jarCacheFile), "Expected artifact saved in build cache.");
    }

    private void verifyWithCache(Verifier verifier) throws VerificationException {
        // Next step : verify
        cleanBuild(verifier);

        verifier.setLogFileName("../log-verify.txt");
        verifier.executeGoal("verify");
        verifier.verifyTextInLog(
                FOUND_CACHED_BUILD_RESTORING_ORG_APACHE_MAVEN_CACHING_TEST_MBUILDCACHE_INCREMENTAL_FROM_CACHE_BY_CHECKSUM);
        verifier.verifyTextInLog(
                "Project org.apache.maven.caching.test:mbuildcache-incremental restored partially. Highest cached goal: package, requested: verify");
        verifier.verifyErrorFreeLog();
        verifySkippedPluginExecutions(verifier);
        verifier.verifyTextInLog(INTEGRATION_TEST_DEFAULT_MBUILDCACHE_INCREMENTAL);
        verifier.verifyTextInLog(VERIFY_DEFAULT_MBUILDCACHE_INCREMENTAL);
        verifier.verifyTextInLog(SAVED_BUILD_TO_LOCAL_FILE);
        verifier.verifyFilePresent(GENERATED_JAR);
        verifyCachedExtraOutputs(verifier);
        Assertions.assertTrue(Files.exists(jarCacheFile), "Expected artifact saved in build cache.");
    }

    private void installWithCache(Verifier verifier) throws VerificationException {
        // Install with clean build, with a higher goal should restore cached mojo executions and apply increments
        cleanBuild(verifier);

        verifier.setLogFileName("../log-install.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(
                "Project org.apache.maven.caching.test:mbuildcache-incremental restored partially. Highest cached goal: verify, requested: install");
        verifySkippedPluginExecutions(verifier);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_FAILSAFE_INTEGRATION_TEST);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_FAILSAFE_VERIFY);
        verifyNoCachedPluginExecutions(verifier);
        verifier.verifyTextInLog(INSTALL_DEFAULT_INSTALL_MBUILDCACHE_INCREMENTAL);
        final String installToLocalRepoString =
                "Installing " + verifier.getBasedir() + File.separatorChar + EXTRA_OUTPUT_1 + " to ";
        verifier.verifyTextInLog(installToLocalRepoString);
        verifier.verifyTextInLog(SAVED_BUILD_TO_LOCAL_FILE);
        verifier.verifyFilePresent(GENERATED_JAR);
        verifyCachedExtraOutputs(verifier);
        Assertions.assertTrue(Files.exists(jarCacheFile), "Expected artifact saved in build cache.");
    }

    private void deployWithCache(Verifier verifier) throws VerificationException {
        // Deploy with clean build, with a higher goal should restore cached mojo executions and apply increments
        cleanBuild(verifier);

        verifier.setLogFileName("../log-deploy.txt");
        verifier.executeGoal("deploy");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(
                "Project org.apache.maven.caching.test:mbuildcache-incremental restored partially. Highest cached goal: install, requested: deploy");
        verifySkippedPluginExecutions(verifier);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_FAILSAFE_INTEGRATION_TEST);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_FAILSAFE_VERIFY);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_INSTALL_INSTALL);
        verifyNoCachedPluginExecutions(verifier);
        verifyNoTextInLog(verifier, INSTALL_DEFAULT_INSTALL_MBUILDCACHE_INCREMENTAL);
        verifier.verifyTextInLog(DEPLOY_DEFAULT_DEPLOY_MBUILDCACHE_INCREMENTAL);
        verifier.verifyTextInLog("Using alternate deployment repository local::file:./target/staging");
        verifier.verifyTextInLog(SAVED_BUILD_TO_LOCAL_FILE);
        verifier.verifyFilePresent(GENERATED_JAR);
        verifier.verifyFilePresent(GENERATED_SOURCES_JAR);
        verifier.verifyFilePresent(GENERATED_JAVADOC_JAR);
        verifyCachedExtraOutputs(verifier);
        Assertions.assertTrue(Files.exists(jarCacheFile), "Expected artifact saved in build cache.");
        Assertions.assertTrue(Files.exists(jarSourcesCacheFile), "Expected sources artifact saved in build cache.");
        Assertions.assertTrue(Files.exists(jarJavadocCacheFile), "Expected javadoc artifact saved in build cache.");
    }

    private void replayInstallWithCache(Verifier verifier) throws VerificationException {
        // Replay install with clean build, with a lower goal should only restore cached mojo executions
        verifier.setLogFileName("../log-install-replay.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(
                FOUND_CACHED_BUILD_RESTORING_ORG_APACHE_MAVEN_CACHING_TEST_MBUILDCACHE_INCREMENTAL_FROM_CACHE_BY_CHECKSUM);
        verifySkippedPluginExecutions(verifier);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_FAILSAFE_INTEGRATION_TEST);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_FAILSAFE_VERIFY);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_INSTALL_INSTALL);
        verifyNoTextInLog(verifier, DEPLOY_DEFAULT_DEPLOY_MBUILDCACHE_INCREMENTAL);
        verifyNoCachedPluginExecutions(verifier);
        verifyNoTextInLog(verifier, SAVED_BUILD_TO_LOCAL_FILE, "Expected successful build cache restore.");
        verifier.verifyFilePresent(GENERATED_JAR);
        verifier.verifyFilePresent(GENERATED_SOURCES_JAR);
        verifier.verifyFilePresent(GENERATED_JAVADOC_JAR);
        verifyCachedExtraOutputs(verifier);
        Assertions.assertTrue(Files.exists(jarCacheFile), "Expected artifact saved in build cache.");
        Assertions.assertTrue(Files.exists(jarSourcesCacheFile), "Expected sources artifact saved in build cache.");
        Assertions.assertTrue(Files.exists(jarJavadocCacheFile), "Expected javadoc artifact saved in build cache.");
    }

    private void cleanBuild(Verifier verifier) throws VerificationException {
        verifier.setMavenDebug(false);
        verifier.setLogFileName("../log-clean.txt");
        verifier.executeGoal("clean");
        verifier.verifyFileNotPresent(GENERATED_JAR);
    }

    private void verifyAllExtraOutputsPresent(Verifier verifier) throws VerificationException {
        verifier.verifyFilePresent(EXTRA_OUTPUT_1);
        verifier.verifyFilePresent(EXTRA_OUTPUT_2);
        verifier.verifyFilePresent(EXTRA_OUTPUT_3);
        verifier.verifyFilePresent(EXTRA_OUTPUT_4);
        verifier.verifyFilePresent(EXTRA_OUTPUT_5);
        verifier.verifyFilePresent(EXTRA_OUTPUT_6);
    }

    private void verifyCachedExtraOutputs(Verifier verifier) throws VerificationException {
        verifier.verifyFilePresent(EXTRA_OUTPUT_1);
        verifier.verifyFilePresent(EXTRA_OUTPUT_2);
        verifier.verifyFileNotPresent(EXTRA_OUTPUT_3);
        verifier.verifyFileNotPresent(EXTRA_OUTPUT_4);
        verifier.verifyFileNotPresent(EXTRA_OUTPUT_5);
        verifier.verifyFilePresent(EXTRA_OUTPUT_6);
    }

    private void verifySkippedPluginExecutions(Verifier verifier) throws VerificationException {
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_RESOURCES_RESOURCES);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_COMPILER_COMPILE);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_RESOURCES_TEST_RESOURCES);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_COMPILER_TEST_COMPILE);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_SUREFIRE_TEST);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_JAR_JAR);
        verifier.verifyTextInLog(SKIPPING_PLUGIN_EXECUTION_CACHED_RESOURCES_COPY);
    }

    private void verifyNoCachedPluginExecutions(Verifier verifier) throws VerificationException {
        verifyNoTextInLog(verifier, RESOURCES_DEFAULT_RESOURCES_MBUILDCACHE_INCREMENTAL);
        verifyNoTextInLog(verifier, COMPILE_DEFAULT_COMPILE_MBUILDCACHE_INCREMENTAL);
        verifyNoTextInLog(verifier, TEST_RESOURCES_DEFAULT_TEST_RESOURCES_MBUILDCACHE_INCREMENTAL);
        verifyNoTextInLog(verifier, TEST_COMPILE_DEFAULT_TEST_COMPILE_MBUILDCACHE_INCREMENTAL);
        verifyNoTextInLog(verifier, TEST_DEFAULT_TEST_MBUILDCACHE_INCREMENTAL);
        verifyNoTextInLog(verifier, JAR_DEFAULT_JAR_MBUILDCACHE_INCREMENTAL);
        verifyNoTextInLog(verifier, INTEGRATION_TEST_DEFAULT_MBUILDCACHE_INCREMENTAL);
        verifyNoTextInLog(verifier, VERIFY_DEFAULT_MBUILDCACHE_INCREMENTAL);
    }

    private static void verifyNoTextInLog(Verifier verifier, String text, String message) throws VerificationException {
        Assertions.assertNull(findFirstLineContainingTextsInLogs(verifier, text), message);
    }

    private static void verifyNoTextInLog(Verifier verifier, String text) throws VerificationException {
        Assertions.assertNull(findFirstLineContainingTextsInLogs(verifier, text));
    }

    private static Path getSavedBuildInfoPath(Verifier verifier) throws VerificationException {
        String savedPathLogLine = findFirstLineContainingTextsInLogs(verifier, SAVED_BUILD_TO_LOCAL_FILE);
        String[] array = savedPathLogLine.split(SAVED_BUILD_TO_LOCAL_FILE);
        return Paths.get(array[array.length - 1]);
    }
}
