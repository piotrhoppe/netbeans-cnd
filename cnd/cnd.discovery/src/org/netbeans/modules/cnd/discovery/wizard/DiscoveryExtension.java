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

package org.netbeans.modules.cnd.discovery.wizard;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.modules.cnd.discovery.projectimport.ImportProject;
import org.netbeans.modules.cnd.makeproject.api.ui.wizard.IteratorExtension;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;

/**
 * Trimmed implementation of {@link IteratorExtension} from the original
 * {@code cnd.discovery} module.
 * <p>
 * Only the entry points used by {@code NewMakeProjectWizardIterator} are kept:
 * <ul>
 *   <li>{@link #createProject(WizardDescriptor)} — fully functional, delegates
 *       to {@link ImportProject} exactly as in the original module.</li>
 *   <li>{@link #discoverProject(Map, Project, ProjectKind)} — no-op stub; the
 *       original delegates to {@code ImportExecutable}, which needs the
 *       discovery provider SPI ({@code cnd.discovery.api}), the C++ code model
 *       ({@code cnd.model.api}) and DWARF/build-log analysis, none of which are
 *       part of this distribution.</li>
 * </ul>
 * The remaining {@code IteratorExtension} methods are no-op stubs for the same
 * reason. This class also no longer implements {@code DiscoveryExtensionInterface},
 * as that SPI lives in the omitted {@code cnd.discovery.api} package.
 * <p>
 * Original source: {@code contrib/cnd.discovery/src/.../wizard/DiscoveryExtension.java}
 * of the Apache NetBeans repository (commit d84e125315).
 */
@org.openide.util.lookup.ServiceProvider(service = org.netbeans.modules.cnd.makeproject.api.ui.wizard.IteratorExtension.class)
public class DiscoveryExtension implements IteratorExtension {

    private static final Logger LOGGER = Logger.getLogger(DiscoveryExtension.class.getName());

    /** Creates a new instance of DiscoveryExtension */
    public DiscoveryExtension() {
    }

    @Override
    public Set<FileObject> createProject(WizardDescriptor wizard) throws IOException {
        return new ImportProject(wizard).create();
    }

    @Override
    public void discoverProject(Map<String, Object> map, Project lastSelectedProject, ProjectKind projectKind) {
        LOGGER.log(Level.INFO, "discoverProject: discovery engine is not available in this distribution"); // NOI18N
    }

    @Override
    public void discoverArtifacts(Map<String, Object> map) {
        LOGGER.log(Level.FINE, "discoverArtifacts: discovery engine is not available in this distribution"); // NOI18N
    }

    @Override
    public void discoverHeadersByModel(Project project) {
        LOGGER.log(Level.FINE, "discoverHeadersByModel: code model is not available in this distribution"); // NOI18N
    }

    @Override
    public void disableModel(Project project) {
        LOGGER.log(Level.FINE, "disableModel: code model is not available in this distribution"); // NOI18N
    }
}
