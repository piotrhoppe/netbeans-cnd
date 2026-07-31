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
package org.netbeans.modules.cnd.discovery.projectimport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.modules.cnd.api.remote.RemoteFileUtil;
import org.netbeans.modules.cnd.api.remote.ServerList;
import org.netbeans.modules.cnd.api.toolchain.CompilerSet;
import org.netbeans.modules.cnd.api.toolchain.CompilerSetManager;
import org.netbeans.modules.cnd.makeproject.api.MakeArtifact;
import org.netbeans.modules.cnd.makeproject.api.MakeProjectOptions;
import org.netbeans.modules.cnd.makeproject.api.ProjectSupport;
import org.netbeans.modules.cnd.makeproject.api.SourceFolderInfo;
import org.netbeans.modules.cnd.makeproject.api.configurations.MakeConfiguration;
import org.netbeans.modules.cnd.makeproject.api.configurations.MakeConfigurationDescriptor;
import org.netbeans.modules.cnd.makeproject.api.ui.wizard.WizardConstants;
import org.netbeans.modules.cnd.makeproject.api.wizards.ProjectGenerator;
import org.netbeans.modules.cnd.utils.CndPathUtilities;
import org.netbeans.modules.cnd.utils.FSPath;
import org.netbeans.modules.nativeexecution.api.ExecutionEnvironment;
import org.netbeans.modules.nativeexecution.api.ExecutionEnvironmentFactory;
import org.netbeans.modules.remote.spi.FileSystemProvider;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;

/**
 * Creates a MakeProject from an existing sources wizard.
 * <p>
 * Trimmed version of the original {@code cnd.discovery} {@code ImportProject}.
 * The project-creation part ({@link #create()} plus wizard parameter parsing) is
 * kept verbatim, since all of its dependencies are available in this fork.
 * <p>
 * The post-creation pipeline of the original class ({@code doWork()}: running
 * configure/make, DWARF and build-log analysis, code model tuning) is
 * <b>not</b> included, because it depends on modules that are outside of this
 * distribution ({@code cnd.model.api}, {@code cnd.builds}, {@code cnd.execution}
 * and the {@code cnd.discovery.api} provider SPI).
 * <p>
 * Original source: {@code contrib/cnd.discovery/src/.../projectimport/ImportProject.java}
 * of the Apache NetBeans repository (commit d84e125315).
 */
public class ImportProject {

    private static final String BUILD_COMMAND = MakeArtifact.MAKE_MACRO + " -f Makefile";  // NOI18N
    private static final String CLEAN_COMMAND = MakeArtifact.MAKE_MACRO + " -f Makefile clean";  // NOI18N
    static final boolean TRACE = Boolean.getBoolean("cnd.discovery.trace.projectimport"); // NOI18N
    public static final Logger logger;

    static {
        logger = Logger.getLogger("org.netbeans.modules.cnd.discovery.projectimport.ImportProject"); // NOI18N
        if (TRACE) {
            logger.setLevel(Level.ALL);
        }
    }

    private final String nativeProjectPath;
    private final FileObject nativeProjectFO;
    private final FSPath projectFolder;
    private String projectName;
    private String makefilePath;
    private String configurePath;
    private String configureRunFolder;
    private String configureArguments;
    private String configureCommand;
    private boolean runConfigure = false;
    private boolean manualCA = false;
    private final String hostUID;
    private final ExecutionEnvironment executionEnvironment;
    private final ExecutionEnvironment fileSystemExecutionEnvironment;
    private final MakeProjectOptions.PathMode pathMode;
    private CompilerSet toolchain;
    private boolean defaultToolchain;
    private String workingDir;
    private String buildCommand = BUILD_COMMAND;
    private String cleanCommand = CLEAN_COMMAND;
    private String buildResult = "";  // NOI18N
    private FileObject dwarfSource;
    private Project makeProject;
    private boolean runMake;
    private String includeDirectories = ""; // NOI18N
    private String macros = ""; // NOI18N
    private Iterator<? extends SourceFolderInfo> sources;
    private Iterator<? extends SourceFolderInfo> tests;
    private String sourceFoldersFilter = null;
    private FileObject configureFileObject;
    private final Map<Step, State> importResult = new EnumMap<>(Step.class);
    private final boolean isFullRemoteProject;
    private boolean resolveSymLinks;
    private boolean useBuildAnalyzer;

    public ImportProject(WizardDescriptor wizard) {
        isFullRemoteProject = WizardConstants.PROPERTY_REMOTE_FILE_SYSTEM_ENV.get(wizard) != null;
        hostUID = WizardConstants.PROPERTY_HOST_UID.get(wizard);
        if (hostUID == null) {
            executionEnvironment = ServerList.getDefaultRecord().getExecutionEnvironment();
        } else {
            executionEnvironment = ExecutionEnvironmentFactory.fromUniqueID(hostUID);
        }
        if (isFullRemoteProject) {
            fileSystemExecutionEnvironment = executionEnvironment;
        } else {
            fileSystemExecutionEnvironment = ExecutionEnvironmentFactory.getLocal();
        }
        pathMode = MakeProjectOptions.getPathMode();
        projectFolder = WizardConstants.PROPERTY_PROJECT_FOLDER.get(wizard);
        nativeProjectPath = WizardConstants.PROPERTY_NATIVE_PROJ_DIR.get(wizard);
        assert nativeProjectPath != null;
        if (isFullRemoteProject) {
            FileObject npfo = WizardConstants.PROPERTY_NATIVE_PROJ_FO.get(wizard);
            // #230539 NPE while creation a full remote project
            if (npfo == null) {
                npfo = FileSystemProvider.getFileObject(executionEnvironment, nativeProjectPath);
                if (logger.isLoggable(Level.INFO)) {
                    String warning = "Null file object for " + nativeProjectPath + " at " + executionEnvironment + //NOI18N
                            ((npfo == null) ? " NOT " : "") + " found at 2-nd attempt"; //NOI18N
                    logger.log(Level.INFO, warning, new Exception(warning));
                }
            } else {
                FileObject npfo2 = FileSystemProvider.getFileObject(executionEnvironment, nativeProjectPath);
                if (!npfo.equals(npfo2)) {
                    String warning = "Inconsistent file objects when creating a project: " + npfo + " vs " + npfo2; //NOI18N
                    logger.log(Level.INFO, warning, new Exception(warning));
                }
            }
            nativeProjectFO = npfo;
        } else {
            nativeProjectFO = WizardConstants.PROPERTY_NATIVE_PROJ_FO.get(wizard);
        }
        if (Boolean.TRUE.equals(WizardConstants.PROPERTY_SIMPLE_MODE.get(wizard))) { // NOI18N
            simpleSetup(wizard);
        } else {
            customSetup(wizard);
        }
    }

    private void simpleSetup(WizardDescriptor wizard) {
        projectName = CndPathUtilities.getBaseName(projectFolder.getPath());
        workingDir = nativeProjectPath;
        runConfigure = Boolean.TRUE.equals(WizardConstants.PROPERTY_RUN_CONFIGURE.get(wizard));
        if (runConfigure) {
            configurePath = WizardConstants.PROPERTY_CONFIGURE_SCRIPT_PATH.get(wizard);
            configureArguments = WizardConstants.PROPERTY_CONFIGURE_SCRIPT_ARGS.get(wizard);
            configureRunFolder = WizardConstants.PROPERTY_CONFIGURE_RUN_FOLDER.get(wizard);
            configureCommand = WizardConstants.PROPERTY_CONFIGURE_COMMAND.get(wizard);
        }
        runMake = Boolean.TRUE.equals(WizardConstants.PROPERTY_RUN_REBUILD.get(wizard));
        if (runMake) {
            makefilePath = WizardConstants.PROPERTY_USER_MAKEFILE_PATH.get(wizard);
            if (makefilePath == null) {
                makefilePath = nativeProjectPath + "/Makefile"; // NOI18N;
            }
            buildCommand = WizardConstants.PROPERTY_BUILD_COMMAND.get(wizard);
            cleanCommand = WizardConstants.PROPERTY_CLEAN_COMMAND.get(wizard);
        }
        toolchain = WizardConstants.PROPERTY_TOOLCHAIN.get(wizard);
        defaultToolchain = Boolean.TRUE.equals(WizardConstants.PROPERTY_TOOLCHAIN_DEFAULT.get(wizard));

        List<SourceFolderInfo> list = new ArrayList<>();
        list.add(new SourceFolderInfo() {

            @Override
            public FileObject getFileObject() {
                return nativeProjectFO;
            }

            @Override
            public String getFolderName() {
                return nativeProjectFO.getNameExt();
            }

            @Override
            public boolean isAddSubfoldersSelected() {
                return true;
            }
        });
        sources = list.iterator();
        sourceFoldersFilter = MakeConfigurationDescriptor.DEFAULT_IGNORE_FOLDERS_PATTERN_EXISTING_PROJECT;
        resolveSymLinks = MakeProjectOptions.getResolveSymbolicLinks();
        useBuildAnalyzer = Boolean.TRUE.equals(WizardConstants.PROPERTY_USE_BUILD_ANALYZER.get(wizard));
    }

    private void customSetup(WizardDescriptor wizard) {
        projectName = WizardConstants.PROPERTY_NAME.get(wizard);
        workingDir = WizardConstants.PROPERTY_WORKING_DIR.get(wizard);
        buildCommand = WizardConstants.PROPERTY_BUILD_COMMAND.get(wizard);
        cleanCommand = WizardConstants.PROPERTY_CLEAN_COMMAND.get(wizard);
        buildResult = WizardConstants.PROPERTY_BUILD_RESULT.get(wizard);
        includeDirectories = WizardConstants.PROPERTY_INCLUDES.get(wizard);
        macros = WizardConstants.PROPERTY_MACROS.get(wizard);
        makefilePath = WizardConstants.PROPERTY_USER_MAKEFILE_PATH.get(wizard);
        configurePath = WizardConstants.PROPERTY_CONFIGURE_SCRIPT_PATH.get(wizard);
        configureRunFolder = WizardConstants.PROPERTY_CONFIGURE_RUN_FOLDER.get(wizard);
        configureArguments = WizardConstants.PROPERTY_CONFIGURE_SCRIPT_ARGS.get(wizard);
        configureCommand = WizardConstants.PROPERTY_CONFIGURE_COMMAND.get(wizard);
        runConfigure = Boolean.TRUE.equals(WizardConstants.PROPERTY_RUN_CONFIGURE.get(wizard));
        sources = WizardConstants.PROPERTY_SOURCE_FOLDERS.get(wizard);
        tests = WizardConstants.PROPERTY_TEST_FOLDERS.get(wizard);
        sourceFoldersFilter = WizardConstants.PROPERTY_SOURCE_FOLDERS_FILTER.get(wizard);
        runMake = Boolean.TRUE.equals(WizardConstants.PROPERTY_RUN_REBUILD.get(wizard));
        manualCA = Boolean.TRUE.equals(WizardConstants.PROPERTY_MANUAL_CODE_ASSISTANCE.get(wizard));
        toolchain = WizardConstants.PROPERTY_TOOLCHAIN.get(wizard);
        defaultToolchain = Boolean.TRUE.equals(WizardConstants.PROPERTY_TOOLCHAIN_DEFAULT.get(wizard));
        Boolean resolve = WizardConstants.PROPERTY_RESOLVE_SYM_LINKS.get(wizard);
        if (resolve != null) {
            resolveSymLinks = resolve;
        } else {
            resolveSymLinks = MakeProjectOptions.getResolveSymbolicLinks();
        }
        useBuildAnalyzer = Boolean.TRUE.equals(WizardConstants.PROPERTY_USE_BUILD_ANALYZER.get(wizard));
    }

    public Set<FileObject> create() throws IOException {
        Set<FileObject> resultSet = new HashSet<>();
        MakeConfiguration extConf;
        String aHostUID = hostUID;
        if (isFullRemoteProject) {
            aHostUID = ExecutionEnvironmentFactory.toUniqueID(ExecutionEnvironmentFactory.getLocal());
            extConf = MakeConfiguration.createMakefileConfiguration(projectFolder, "Default", aHostUID, toolchain, defaultToolchain); // NOI18N
            int platform = CompilerSetManager.get(executionEnvironment).getPlatform();
            extConf.getDevelopmentHost().setBuildPlatform(platform);
        } else {
            extConf = MakeConfiguration.createConfiguration(projectFolder, "Default", MakeConfiguration.TYPE_MAKEFILE, null, aHostUID, toolchain, defaultToolchain); // NOI18N
        }
        if (runConfigure) {
            if (configureRunFolder != null && !configureRunFolder.isEmpty()) {
                String workingDirRel = ProjectSupport.toProperPath(projectFolder, CndPathUtilities.naturalizeSlashes(configureRunFolder), pathMode);
                workingDirRel = CndPathUtilities.normalizeSlashes(workingDirRel);
                extConf.getPreBuildConfiguration().getPreBuildCommandWorkingDir().setValue(workingDirRel);
                extConf.getPreBuildConfiguration().getPreBuildCommand().setValue(configureCommand);
            }
        }
        String workingDirRel = ProjectSupport.toProperPath(projectFolder, CndPathUtilities.naturalizeSlashes(workingDir), pathMode);
        workingDirRel = CndPathUtilities.normalizeSlashes(workingDirRel);
        extConf.getMakefileConfiguration().getBuildCommandWorkingDir().setValue(workingDirRel);
        extConf.getMakefileConfiguration().getBuildCommand().setValue(buildCommand);
        extConf.getMakefileConfiguration().getCleanCommand().setValue(cleanCommand);
        // Build result
        if (buildResult != null && buildResult.length() > 0) {
            FileObject fo = RemoteFileUtil.getFileObject(buildResult, fileSystemExecutionEnvironment);
            if (fo != null && fo.isValid()) {
                dwarfSource = fo;
            }
            if (fo != null && fo.isValid() && fo.isFolder()) {
                // do not set build result
            } else {
                buildResult = ProjectSupport.toProperPath(projectFolder, CndPathUtilities.naturalizeSlashes(buildResult), pathMode);
                buildResult = CndPathUtilities.normalizeSlashes(buildResult);
                extConf.getMakefileConfiguration().getOutput().setValue(buildResult);
            }
        }
        extConf.getProfile().setRunDirectory(workingDirRel);
        extConf.getProfile().setBuildFirst(false);
        // Include directories
        if (includeDirectories != null && includeDirectories.length() > 0) {
            StringTokenizer tokenizer = new StringTokenizer(includeDirectories, ";"); // NOI18N
            List<String> includeDirectoriesVector = new ArrayList<>();
            while (tokenizer.hasMoreTokens()) {
                String includeDirectory = tokenizer.nextToken();
                includeDirectory = CndPathUtilities.toRelativePath(projectFolder.getPath(), CndPathUtilities.naturalizeSlashes(includeDirectory));
                includeDirectory = CndPathUtilities.normalizeSlashes(includeDirectory);
                includeDirectoriesVector.add(includeDirectory);
            }
            extConf.getCCompilerConfiguration().getIncludeDirectories().setValue(includeDirectoriesVector);
            extConf.getCCCompilerConfiguration().getIncludeDirectories().setValue(new ArrayList<>(includeDirectoriesVector));
        }
        extConf.getCodeAssistanceConfiguration().getResolveSymbolicLinks().setValue(resolveSymLinks);
        extConf.getCodeAssistanceConfiguration().getBuildAnalyzer().setValue(useBuildAnalyzer);

        // Macros
        if (macros != null && macros.length() > 0) {
            StringTokenizer tokenizer = new StringTokenizer(macros, "; "); // NOI18N
            ArrayList<String> list = new ArrayList<>();
            while (tokenizer.hasMoreTokens()) {
                list.add(tokenizer.nextToken());
            }
            // FIXUP
            extConf.getCCompilerConfiguration().getPreprocessorConfiguration().getValue().addAll(list);
            extConf.getCCCompilerConfiguration().getPreprocessorConfiguration().getValue().addAll(list);
        }
        // Add makefile and configure script to important files
        ArrayList<String> importantItems = new ArrayList<>();
        if (makefilePath != null && makefilePath.length() > 0) {
            makefilePath = ProjectSupport.toProperPath(projectFolder, CndPathUtilities.naturalizeSlashes(makefilePath), pathMode);
            makefilePath = CndPathUtilities.normalizeSlashes(makefilePath);
        }
        if (configurePath != null && configurePath.length() > 0) {
            String normPath = RemoteFileUtil.normalizeAbsolutePath(configurePath, fileSystemExecutionEnvironment);
            configureFileObject = RemoteFileUtil.getFileObject(normPath, fileSystemExecutionEnvironment);
            configurePath = ProjectSupport.toProperPath(projectFolder, CndPathUtilities.naturalizeSlashes(configurePath), pathMode);
            configurePath = CndPathUtilities.normalizeSlashes(configurePath);
            importantItems.add(configurePath);
        }
        {
            String launcher = projectFolder.getPath() + "/nbproject/private/launcher.properties"; //NOI18N
            launcher = ProjectSupport.toProperPath(projectFolder, CndPathUtilities.naturalizeSlashes(launcher), pathMode);
            launcher = CndPathUtilities.normalizeSlashes(launcher);
            importantItems.add(launcher);
        }
        Iterator<String> importantItemsIterator = importantItems.iterator();
        if (!importantItemsIterator.hasNext()) {
            importantItemsIterator = null;
        }
        ProjectGenerator.ProjectParameters prjParams = new ProjectGenerator.ProjectParameters(projectName, projectFolder);
        prjParams
                .setConfiguration(extConf)
                .setSourceFolders(Collections.<SourceFolderInfo>emptyList().iterator())
//                .setSourceFolders(sources)
                .setSourceFoldersFilter(sourceFoldersFilter)
                .setTestFolders(tests)
                .setImportantFiles(importantItemsIterator)
                .setFullRemoteNativeProjectPath(nativeProjectPath)
                .setHostUID(aHostUID);
        if (makefilePath != null) {
            prjParams.setMakefileName(makefilePath);
        } else {
            prjParams.setMakefileName(""); //NOI18N
        }
        makeProject = ProjectGenerator.getDefault().createProject(prjParams);
        FileObject dir = projectFolder.getFileObject();
        importResult.put(Step.Project, State.Successful);
        resultSet.add(dir);
        return resultSet;
    }

    /**
     * @return the created project, or {@code null} if {@link #create()} has not
     *         been called yet or failed.
     */
    public Project getProject() {
        return makeProject;
    }

    /**
     * @return result of every performed import step.
     */
    public Map<Step, State> getState() {
        return Collections.unmodifiableMap(importResult);
    }

    /**
     * @return source folders selected in the wizard.
     */
    public Iterator<? extends SourceFolderInfo> getSources() {
        return sources;
    }

    /**
     * @return build artifact selected in the wizard, or {@code null}.
     */
    public FileObject getBuildArtifact() {
        return dwarfSource;
    }

    /**
     * @return configure script selected in the wizard, or {@code null}.
     */
    public FileObject getConfigureFileObject() {
        return configureFileObject;
    }

    /**
     * @return arguments of the configure script, or {@code null}.
     */
    public String getConfigureArguments() {
        return configureArguments;
    }

    /**
     * @return {@code true} if the wizard requested to rebuild the project.
     */
    public boolean isRunMake() {
        return runMake;
    }

    /**
     * @return {@code true} if code assistance is configured manually.
     */
    public boolean isManualCodeAssistance() {
        return manualCA;
    }

    public static enum State {

        Successful, Fail, Skiped
    }

    public static enum Step {

        Project, Configure, MakeClean, Make, DiscoveryDwarf, DiscoveryLog, FixMacros, DiscoveryModel, FixExcluded
    }
}
