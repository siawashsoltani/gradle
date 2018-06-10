/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.MetadataResolutionContext
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataImpl
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataImpl
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory
import org.gradle.api.internal.changedetection.state.ValueSnapshotter
import org.gradle.api.internal.notations.DependencyMetadataNotationParser
import org.gradle.api.internal.notations.ModuleIdentifierNotationConverter
import org.gradle.cache.CacheRepository
import org.gradle.internal.action.DefaultConfigurableRule
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultMutableIvyModuleResolveMetadata
import org.gradle.internal.component.external.model.DefaultMutableMavenModuleResolveMetadata
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.util.BuildCommencedTimeProvider
import org.gradle.util.TestUtil
import org.gradle.util.internal.SimpleMapInterner
import spock.lang.Specification

class DefaultComponentMetadataProcessorTest extends Specification {

    private static final String GROUP = "group"
    private static final String MODULE = "module"

    MetadataResolutionContext context = Mock()
    def executor = new ComponentMetadataRuleExecutor(Stub(CacheRepository), Stub(InMemoryCacheDecoratorFactory), Stub(ValueSnapshotter), new BuildCommencedTimeProvider(), Stub(Serializer))
    def instantiator = DirectInstantiator.INSTANCE
    def stringInterner = SimpleMapInterner.notThreadSafe()
    def mavenMetadataFactory = new MavenMutableModuleMetadataFactory(new DefaultImmutableModuleIdentifierFactory(), TestUtil.attributesFactory(), TestUtil.objectInstantiator(), TestUtil.featurePreviews())
    def ivyMetadataFactory = new IvyMutableModuleMetadataFactory(new DefaultImmutableModuleIdentifierFactory(), TestUtil.attributesFactory())
    def dependencyMetadataNotationParser = DependencyMetadataNotationParser.parser(instantiator, DirectDependencyMetadataImpl, stringInterner)
    def dependencyConstraintMetadataNotationParser = DependencyMetadataNotationParser.parser(instantiator, DependencyConstraintMetadataImpl, stringInterner)
    def moduleIdentifierNotationParser = NotationParserBuilder.toType(ModuleIdentifier).converter(new ModuleIdentifierNotationConverter(new DefaultImmutableModuleIdentifierFactory())).toComposite();

    def 'setup'() {
        TestComponentMetadataRule.instanceCount = 0
        TestComponentMetadataRuleWithArgs.instanceCount = 0
        TestComponentMetadataRuleWithArgs.constructorParams = null
    }

    def "does nothing when no rules registered"() {
        def processor = new DefaultComponentMetadataProcessor([] as Set, [] as Set, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, TestUtil.attributesFactory(), executor, context)
        def metadata = ivyMetadata().asImmutable()

        expect:
        processor.processMetadata(metadata).is(metadata)
    }

    def "instantiates class rule when processing metadata"() {
        given:
        context.injectingInstantiator >> instantiator
        String notation = "${GROUP}:${MODULE}"
        def processor = new DefaultComponentMetadataProcessor([] as Set, [ruleForModule(notation)] as Set, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, TestUtil.attributesFactory(), executor, context)


        when:
        processor.processMetadata(ivyMetadata().asImmutable())

        then:
        TestComponentMetadataRule.instanceCount == 1
    }

    def "instantiates class rule with params when processing metadata"() {
        given:
        context.injectingInstantiator >> instantiator
        String notation = "${GROUP}:${MODULE}"
        def processor = new DefaultComponentMetadataProcessor([] as Set, [ruleForModuleWithParams(notation, "foo", 42L)] as Set, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, TestUtil.attributesFactory(), executor, context)

        when:
        processor.processMetadata(ivyMetadata().asImmutable())

        then:
        TestComponentMetadataRuleWithArgs.instanceCount == 1
        TestComponentMetadataRuleWithArgs.constructorParams == ["foo", 42L] as Object[]
    }

    def "processing fails when status is not present in status scheme"() {
        def processor = new DefaultComponentMetadataProcessor([] as Set, [] as Set, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, TestUtil.attributesFactory(), executor, context)
        def metadata = ivyMetadata()
        metadata.status = "green"
        metadata.statusScheme = ["alpha", "beta"]

        when:
        processor.processMetadata(metadata.asImmutable())

        then:
        ModuleVersionResolveException e = thrown()
        e.message == /Unexpected status 'green' specified for group:module:version. Expected one of: [alpha, beta]/
    }

    private SpecConfigurableRule ruleForModule(String notation) {
        return new SpecConfigurableRule(DefaultConfigurableRule.of(TestComponentMetadataRule), new DefaultComponentMetadataHandler.ModuleVersionIdentifierSpec(moduleIdentifierNotationParser.parseNotation(notation)))
    }

    private SpecConfigurableRule ruleForModuleWithParams(String notation, Object... params) {
        return new SpecConfigurableRule(DefaultConfigurableRule.of(TestComponentMetadataRuleWithArgs, {
            it.params(params)
        } as Action<ActionConfiguration>, TestUtil.valueSnapshotter()), new DefaultComponentMetadataHandler.ModuleVersionIdentifierSpec(moduleIdentifierNotationParser.parseNotation(notation)))
    }

    private DefaultMutableIvyModuleResolveMetadata ivyMetadata() {
        def module = DefaultModuleIdentifier.newId("group", "module")
        def metadata = ivyMetadataFactory.create(DefaultModuleComponentIdentifier.newId(module, "version"))
        metadata.status = "integration"
        metadata.statusScheme = ["integration", "release"]
        return metadata
    }

    private DefaultMutableMavenModuleResolveMetadata mavenMetadata() {
        def module = DefaultModuleIdentifier.newId("group", "module")
        def metadata = mavenMetadataFactory.create(DefaultModuleComponentIdentifier.newId(module, "version"))
        metadata.status = "integration"
        metadata.statusScheme = ["integration", "release"]
        return metadata
    }
}
