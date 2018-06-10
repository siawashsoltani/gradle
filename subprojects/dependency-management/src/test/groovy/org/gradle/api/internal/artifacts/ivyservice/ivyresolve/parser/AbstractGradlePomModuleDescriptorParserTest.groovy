/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.apache.ivy.core.module.id.ArtifactRevisionId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.external.model.MavenDependencyDescriptor
import org.gradle.internal.component.external.model.MutableMavenModuleResolveMetadata
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

abstract class AbstractGradlePomModuleDescriptorParserTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = new DefaultImmutableModuleIdentifierFactory()
    final MavenMutableModuleMetadataFactory mavenMetadataFactory = new MavenMutableModuleMetadataFactory(moduleIdentifierFactory, TestUtil.attributesFactory(), TestUtil.objectInstantiator(), TestUtil.featurePreviews())
    final FileResourceRepository fileRepository = TestFiles.fileRepository()
    final GradlePomModuleDescriptorParser parser = new GradlePomModuleDescriptorParser(new DefaultVersionSelectorScheme(new DefaultVersionComparator(), new VersionParser()), moduleIdentifierFactory, fileRepository, mavenMetadataFactory)
    final parseContext = Mock(DescriptorParseContext)
    TestFile pomFile
    MutableMavenModuleResolveMetadata metadata

    def "setup"() {
        pomFile = tmpDir.file('test-pom.xml')
    }

    protected void parsePom() {
        metadata = parseMetaData()
    }

    protected LocallyAvailableExternalResource asResource(File file) {
        return fileRepository.resource(file)
    }

    protected MutableMavenModuleResolveMetadata parseMetaData() {
        parser.parseMetaData(parseContext, pomFile, true)
    }

    protected void hasDefaultDependencyArtifact(MavenDependencyDescriptor descriptor) {
        assert descriptor.dependencyArtifact == null
    }

    protected void hasDependencyArtifact(MavenDependencyDescriptor descriptor, String name, String type, String ext, String classifier = null) {
        def artifact = descriptor.dependencyArtifact
        assert artifact.name == name
        assert artifact.type == type
        assert artifact.extension == ext
        assert artifact.classifier == classifier
    }

    protected static ModuleComponentIdentifier componentId(String group, String name, String version) {
        DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(group, name), version)
    }

    protected static ModuleComponentSelector moduleId(String group, String name, String version) {
        DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(group, name), new DefaultMutableVersionConstraint(version))
    }

    protected ArtifactRevisionId artifactId(ModuleRevisionId moduleId, String name, String type, String ext) {
        ArtifactRevisionId.newInstance(moduleId, name, type, ext)
    }

    static <T> T single(Iterable<T> elements) {
        assert elements.size() == 1
        return elements.first()
    }
}
