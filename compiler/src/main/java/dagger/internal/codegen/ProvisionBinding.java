/*
 * Copyright (C) 2014 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Supplier;
import com.google.common.collect.*;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.Replaceable;
import dagger.internal.codegen.ComponentDescriptor.BuilderRequirementMethod;

import javax.annotation.CheckReturnValue;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.Preconditions.*;
import static com.google.common.base.Suppliers.memoize;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.MapKeys.getMapKey;
import static dagger.internal.codegen.MoreAnnotationMirrors.wrapOptionalInEquivalence;
import static dagger.internal.codegen.Util.toImmutableList;
import static javax.lang.model.element.ElementKind.*;

/**
 * A value object representing the mechanism by which a {@link Key} can be provided. New instances
 * should be created using an instance of the {@link Factory}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ProvisionBinding extends ContributionBinding {

    @Override
    ImmutableSet<DependencyRequest> implicitDependencies() {
        return membersInjectionRequest().isPresent()
                ? ImmutableSet.of(membersInjectionRequest().get())
                : ImmutableSet.of();
    }

    abstract ImmutableSet<DependencyRequest> stubDependencies();

    /**
     * If this provision requires members injection, this will be the corresponding request.
     */
    abstract Optional<DependencyRequest> membersInjectionRequest();

    @Override
    public BindingType bindingType() {
        return BindingType.PROVISION;
    }

    @Override
    public boolean shouldGenerateDelegate() {
        return Util.bindingSupportsTestDelegate(this); //&& bindingKind() != Kind.SYNTHETIC_DELEGATE_BINDING;
    }

    @Override
    abstract Optional<ProvisionBinding> unresolved();

    @Override
    abstract Optional<Scope> scope();

    private static Builder builder() {
        return new AutoValue_ProvisionBinding.Builder()
                .genericParameter(false)
                .ignoreStubGeneration(true)
                .generateTestDelegate(false)
                .explicitDependencies(ImmutableSet.<DependencyRequest>of())
                .stubDependencies(ImmutableSet.of());
    }

    ImmutableList<DependencyAssociation> stubDependencyAssocations() {
        return this.stubDependencyAssociations.get();
    }

    ImmutableList<FrameworkDependency> stubFrameworkDependencies() {
        return frameworkDependencies.get();
    }

    private final Supplier<ImmutableList<FrameworkDependency>> frameworkDependencies =
            memoize(
                    () ->
                            stubDependencyAssocations()
                                    .stream()
                                    .map(DependencyAssociation::frameworkDependency)
                                    .collect(toImmutableList()));

    abstract Builder toBuilder();

    private final Supplier<ImmutableList<DependencyAssociation>> stubDependencyAssociations =
            memoize(
                    () -> {
                        BindingTypeMapper bindingTypeMapper = BindingTypeMapper.forBindingType(bindingType());
                        ImmutableList.Builder<DependencyAssociation> list = ImmutableList.builder();
                        for (Collection<DependencyRequest> requests : groupStubDependenciesByUnresolvedKey()) {
                            list.add(
                                    DependencyAssociation.create(
                                            FrameworkDependency.create(
                                                    getOnlyElement(
                                                            requests.stream()
                                                                    .map(DependencyRequest::bindingKey)
                                                                    .collect(Collectors.toSet())),
                                                    bindingTypeMapper.getBindingType(requests)),
                                            requests));
                        }
                        return list.build();
                    });


    private ImmutableList<Collection<DependencyRequest>> groupStubDependenciesByUnresolvedKey() {
        ImmutableSetMultimap.Builder<BindingKey, DependencyRequest> dependenciesByKeyBuilder =
                ImmutableSetMultimap.builder();
        Iterator<DependencyRequest> dependencies = stubDependencies().iterator();
        while (dependencies.hasNext()) {
            final DependencyRequest next = dependencies.next();
            dependenciesByKeyBuilder.put(next.bindingKey(), next);
        }
        return ImmutableList.copyOf(
                dependenciesByKeyBuilder
                        .orderValuesBy(SourceFiles.DEPENDENCY_ORDERING)
                        .build()
                        .asMap()
                        .values());
    }

    @AutoValue.Builder
    @CanIgnoreReturnValue
    abstract static class Builder extends ContributionBinding.Builder<Builder> {

        abstract Builder membersInjectionRequest(Optional<DependencyRequest> membersInjectionRequest);

        abstract Builder unresolved(ProvisionBinding unresolved);

        abstract Builder stubDependencies(ImmutableSet<DependencyRequest> stubDependencies);

        abstract Builder scope(Optional<Scope> scope);

        @CheckReturnValue
        abstract ProvisionBinding build();
    }

    static final class Factory {
        private final Elements elements;
        private final Types types;
        private final Key.Factory keyFactory;
        private final DependencyRequest.Factory dependencyRequestFactory;
        private final AppConfig.Provider appConfigProvider;

        Factory(Elements elements, Types types, Key.Factory keyFactory,
                DependencyRequest.Factory dependencyRequestFactory,
                AppConfig.Provider appConfigProvider) {
            this.elements = elements;
            this.types = types;
            this.keyFactory = keyFactory;
            this.dependencyRequestFactory = dependencyRequestFactory;
            this.appConfigProvider = appConfigProvider;
        }

        /**
         * Returns a ProvisionBinding for the given element. If {@code resolvedType} is present, this
         * will return a resolved binding, with the key and type resolved to the given type (using
         * {@link Types#asMemberOf(DeclaredType, Element)}).
         */
        ProvisionBinding forInjectConstructor(
                ExecutableElement constructorElement, Optional<TypeMirror> resolvedType) {
            checkNotNull(constructorElement);
            checkArgument(constructorElement.getKind().equals(CONSTRUCTOR));
            checkArgument(isAnnotationPresent(constructorElement, Inject.class));
            checkArgument(!getQualifier(constructorElement).isPresent());

            ExecutableType cxtorType = MoreTypes.asExecutable(constructorElement.asType());
            DeclaredType enclosingCxtorType =
                    MoreTypes.asDeclared(constructorElement.getEnclosingElement().asType());
            // If the class this is constructing has some type arguments, resolve everything.
            if (!enclosingCxtorType.getTypeArguments().isEmpty() && resolvedType.isPresent()) {
                DeclaredType resolved = MoreTypes.asDeclared(resolvedType.get());
                // Validate that we're resolving from the correct type.
                checkState(types.isSameType(types.erasure(resolved), types.erasure(enclosingCxtorType)),
                        "erased expected type: %s, erased actual type: %s",
                        types.erasure(resolved), types.erasure(enclosingCxtorType));
                cxtorType = MoreTypes.asExecutable(types.asMemberOf(resolved, constructorElement));
                enclosingCxtorType = resolved;
            }

            Key key = keyFactory.forInjectConstructorWithResolvedType(enclosingCxtorType);
            checkArgument(!key.qualifier().isPresent());
            ImmutableSet<DependencyRequest> dependencies =
                    dependencyRequestFactory.forRequiredResolvedVariables(
                            constructorElement.getParameters(), cxtorType.getParameterTypes());
            Optional<DependencyRequest> membersInjectionRequest =
                    membersInjectionRequest(enclosingCxtorType);

            final boolean ignoreStubGeneration = constructorElement.getEnclosingElement().getAnnotation(Replaceable.class) == null;
            Builder builder =
                    ProvisionBinding.builder()
                            .contributionType(ContributionType.UNIQUE)
                            .bindingElement(constructorElement)
                            .ignoreStubGeneration(ignoreStubGeneration)
                            .generateTestDelegate(!ignoreStubGeneration && appConfigProvider.get().debug())
                            .key(key)
                            .explicitDependencies(dependencies)
                            .membersInjectionRequest(membersInjectionRequest)
                            .bindingKind(Kind.INJECTION)
                            .scope(Scope.uniqueScopeOf(constructorElement.getEnclosingElement()));

            TypeElement bindingTypeElement =
                    MoreElements.asType(constructorElement.getEnclosingElement());
            if (hasNonDefaultTypeParameters(bindingTypeElement, key.type(), types)) {
                builder.unresolved(forInjectConstructor(constructorElement, Optional.empty()));
            }
            return builder.build();
        }

        private static final ImmutableSet<ElementKind> MEMBER_KINDS =
                Sets.immutableEnumSet(METHOD, FIELD);

        private Optional<DependencyRequest> membersInjectionRequest(DeclaredType type) {
            return this.membersInjectionRequest(type, type);
        }

        private Optional<DependencyRequest> membersInjectionRequest(DeclaredType type, DeclaredType original) {
            TypeElement typeElement = MoreElements.asType(type.asElement());
            if (!types.isSameType(elements.getTypeElement(Object.class.getCanonicalName()).asType(),
                    typeElement.getSuperclass()) && typeElement.getSuperclass().getKind() != TypeKind.NONE) {
                Optional<DependencyRequest> result = this.membersInjectionRequest(MoreTypes.asDeclared(typeElement.getSuperclass()), original);
                if (result.isPresent()) {
                    return result;
                }
            }
            for (Element enclosedElement : typeElement.getEnclosedElements()) {
                if (MEMBER_KINDS.contains(enclosedElement.getKind())
                        && (isAnnotationPresent(enclosedElement, Inject.class))) {
                    return Optional.of(dependencyRequestFactory.forMembersInjectedType(original));
                }
            }
            return Optional.empty();
        }

        ProvisionBinding forProvidesMethod(
                ExecutableElement providesMethod, TypeElement contributedBy) {
            checkArgument(providesMethod.getKind().equals(METHOD));
            final DeclaredType declaredType = (DeclaredType) contributedBy.asType();
            boolean genericParameter = !declaredType.getTypeArguments().isEmpty();
            ExecutableType resolvedMethod =
                    MoreTypes.asExecutable(
                            types.asMemberOf(MoreTypes.asDeclared(contributedBy.asType()), providesMethod));
            Key key = keyFactory.forProvidesMethod(providesMethod, contributedBy);
            ImmutableSet<DependencyRequest> dependencies =
                    dependencyRequestFactory.forRequiredResolvedVariables(
                            providesMethod.getParameters(),
                            resolvedMethod.getParameterTypes());
            final boolean ignoreStubGeneration = genericParameter || providesMethod.getAnnotation(Replaceable.class) == null;
            return ProvisionBinding.builder()
                    .contributionType(ContributionType.fromBindingMethod(providesMethod))
                    .bindingElement(providesMethod)
                    .ignoreStubGeneration(ignoreStubGeneration)
                    .generateTestDelegate(!ignoreStubGeneration && (appConfigProvider.get().debug()))
                    .contributingModule(Optional.of(contributedBy))
                    .key(key)
                    .genericParameter(genericParameter)
                    .explicitDependencies(dependencies)
                    .nullableType(ConfigurationAnnotations.getNullableType(providesMethod))
                    .wrappedMapKey(wrapOptionalInEquivalence(getMapKey(providesMethod)))
                    .bindingKind(Kind.PROVISION)
                    .scope(Scope.uniqueScopeOf(providesMethod))
                    .build();
        }

        /**
         * A synthetic binding of {@code Map<K, V>} that depends on {@code Map<K, Provider<V>>}.
         */
        ProvisionBinding syntheticMapOfValuesBinding(Key mapOfValuesKey) {
            checkNotNull(mapOfValuesKey);
            Optional<Key> mapOfProvidersKey = keyFactory.implicitMapProviderKeyFrom(mapOfValuesKey);
            checkArgument(mapOfProvidersKey.isPresent(), "%s is not a key for Map<K, V>", mapOfValuesKey);
            DependencyRequest requestForMapOfProviders =
                    dependencyRequestFactory.providerForImplicitMapBinding(mapOfProvidersKey.get());
            return ProvisionBinding.builder()
                    .contributionType(ContributionType.UNIQUE)
                    .key(mapOfValuesKey)
                    .explicitDependencies(requestForMapOfProviders)
                    .bindingKind(Kind.SYNTHETIC_MAP)
                    .build();
        }

        /**
         * A synthetic binding that depends explicitly on a set of individual provision multibinding
         * contribution methods.
         * <p>
         * <p>Note that these could be set multibindings or map multibindings.
         */
        ProvisionBinding syntheticMultibinding(
                Key key, Iterable<ContributionBinding> multibindingContributions) {
            return ProvisionBinding.builder()
                    .contributionType(ContributionType.UNIQUE)
                    .key(key)
                    .explicitDependencies(
                            dependencyRequestFactory.forMultibindingContributions(multibindingContributions))
                    .bindingKind(Kind.forMultibindingKey(key))
                    .build();
        }

        ProvisionBinding forComponent(TypeElement componentDefinitionType) {
            checkNotNull(componentDefinitionType);
            return ProvisionBinding.builder()
                    .contributionType(ContributionType.UNIQUE)
                    .bindingElement(componentDefinitionType)
                    .key(keyFactory.forComponent(componentDefinitionType.asType()))
                    .bindingKind(Kind.COMPONENT)
                    .build();
        }

        ProvisionBinding forComponentMethod(ExecutableElement componentMethod) {
            checkNotNull(componentMethod);
            checkArgument(componentMethod.getKind().equals(METHOD));
            checkArgument(componentMethod.getParameters().isEmpty());
            return ProvisionBinding.builder()
                    .contributionType(ContributionType.UNIQUE)
                    .bindingElement(componentMethod)
                    .key(keyFactory.forComponentMethod(componentMethod))
                    .nullableType(ConfigurationAnnotations.getNullableType(componentMethod))
                    .bindingKind(Kind.COMPONENT_PROVISION)
                    .scope(Scope.uniqueScopeOf(componentMethod))
                    .build();
        }

        ProvisionBinding forBuilderBinding(BuilderRequirementMethod method) {
            ExecutableElement builderMethod = method.method();

            checkNotNull(builderMethod);
            checkArgument(builderMethod.getKind().equals(METHOD));
            checkArgument(builderMethod.getParameters().size() == 1);
            VariableElement parameterElement = Iterables.getOnlyElement(builderMethod.getParameters());
            final boolean ignoreStubGeneration = builderMethod.getAnnotation(Replaceable.class) == null;
            return ProvisionBinding.builder()
                    .contributionType(ContributionType.UNIQUE)
                    .bindingElement(builderMethod)
                    .ignoreStubGeneration(ignoreStubGeneration)
                    .generateTestDelegate(!ignoreStubGeneration && (appConfigProvider.get().debug()))
                    .key(method.requirement().key().get())
                    .nullableType(ConfigurationAnnotations.getNullableType(parameterElement))
                    .bindingKind(Kind.BUILDER_BINDING)
                    .build();
        }

        ProvisionBinding forSubcomponentBuilderMethod(
                ExecutableElement subcomponentBuilderMethod, TypeElement contributedBy, TypeMirror application) {
            checkNotNull(subcomponentBuilderMethod);
            checkArgument(subcomponentBuilderMethod.getKind().equals(METHOD));
            checkArgument(subcomponentBuilderMethod.getParameters().isEmpty());
            DeclaredType declaredContainer = asDeclared(contributedBy.asType());
            final DependencyRequest request = dependencyRequestFactory.plantDependency(application);
            final boolean ignoreStubGeneration = subcomponentBuilderMethod.getAnnotation(Replaceable.class) == null;
            return ProvisionBinding.builder()
                    .contributionType(ContributionType.UNIQUE)
                    .ignoreStubGeneration(ignoreStubGeneration)
                    .generateTestDelegate(!ignoreStubGeneration && (appConfigProvider.get().debug()))
                    .bindingElement(subcomponentBuilderMethod)
                    .key(keyFactory.forSubcomponentBuilderMethod(subcomponentBuilderMethod, declaredContainer))
                    .bindingKind(Kind.SUBCOMPONENT_BUILDER)
                    .explicitDependencies(request)
                    .build();
        }

        ProvisionBinding syntheticSubcomponentBuilder(
                ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations, TypeMirror application) {
            SubcomponentDeclaration subcomponentDeclaration = subcomponentDeclarations.iterator().next();
            final DependencyRequest request = dependencyRequestFactory.plantDependency(application);
            return ProvisionBinding.builder()
                    .contributionType(ContributionType.UNIQUE)
                    .key(subcomponentDeclaration.key())
                    .bindingKind(Kind.SUBCOMPONENT_BUILDER)
                    .explicitDependencies(request)
                    .build();
        }

        ProvisionBinding delegate(
                DelegateDeclaration delegateDeclaration, ProvisionBinding delegate) {
            return delegateBuilder(delegateDeclaration, Optional.of(delegate)).nullableType(delegate.nullableType()).build();
        }

        /**
         * A form of {@link #delegate(DelegateDeclaration, ProvisionBinding)} when the right-hand-side
         * of a {@link Binds} method cannot be resolved.
         */
        ProvisionBinding missingDelegate(DelegateDeclaration delegateDeclaration) {
            return delegateBuilder(delegateDeclaration, Optional.empty()).build();
        }

        private Builder delegateBuilder(
                DelegateDeclaration delegateDeclaration,
                Optional<ProvisionBinding> delegate) {
            boolean ignoreStubGeneration = true;
            final Optional<Element> element = delegateDeclaration.bindingElement();
            if (element.isPresent()) {
                ignoreStubGeneration = element.get().getAnnotation(Replaceable.class) == null;
            }else {
                final Optional<Element> requestElement = delegateDeclaration.delegateRequest().requestElement();
                if(requestElement.isPresent()) {
                    ignoreStubGeneration = requestElement.get().getAnnotation(Replaceable.class) == null;
                }
            }
            return ProvisionBinding.builder()
                    .contributionType(delegateDeclaration.contributionType())
                    .bindingElement(delegateDeclaration.bindingElement().get())
                    .contributingModule(delegateDeclaration.contributingModule())
                    .key(keyFactory.forDelegateBinding(delegateDeclaration, Provider.class))
                    .explicitDependencies(delegateDeclaration.delegateRequest())
                    .ignoreStubGeneration(ignoreStubGeneration)
                    .stubDependencies(delegate.isPresent() ? delegate.get().explicitDependencies() : ImmutableSet.of())
                    .generateTestDelegate(!ignoreStubGeneration && appConfigProvider.get().debug())
                    .wrappedMapKey(delegateDeclaration.wrappedMapKey())
                    .bindingKind(Kind.SYNTHETIC_DELEGATE_BINDING)
                    .scope(Scope.uniqueScopeOf(delegateDeclaration.bindingElement().get()));
        }

        /**
         * Returns a synthetic binding for a {@code @ForReleasableReferences(scope)
         * ReleasableReferenceManager} that provides the component-instantiated object.
         */
        ProvisionBinding provideReleasableReferenceManager(Scope scope) {
            return ProvisionBinding.builder()
                    .contributionType(ContributionType.UNIQUE)
                    .key(keyFactory.forReleasableReferenceManager(scope))
                    .bindingKind(Kind.SYNTHETIC_RELEASABLE_REFERENCE_MANAGER)
                    .build();
        }

        /**
         * Returns a synthetic binding for a {@code @ForReleasableReferences(scope)
         * TypedReleasableReferenceManager<metadataType>} that provides the component-instantiated
         * object.
         */
        ContributionBinding provideTypedReleasableReferenceManager(
                Scope scope, DeclaredType metadataType) {
            return provideReleasableReferenceManager(scope)
                    .toBuilder()
                    .key(keyFactory.forTypedReleasableReferenceManager(scope, metadataType))
                    .build();
        }

        /**
         * Returns a synthetic binding for {@code Set<ReleasableReferenceManager>}.
         */
        ProvisionBinding provideSetOfReleasableReferenceManagers() {
            return ProvisionBinding.builder()
                    .contributionType(ContributionType.UNIQUE)
                    .key(keyFactory.forSetOfReleasableReferenceManagers())
                    .bindingKind(Kind.SYNTHETIC_RELEASABLE_REFERENCE_MANAGERS)
                    .build();
        }

        /**
         * Returns a synthetic binding for {@code Set<TypedReleasableReferenceManager<metadataType>}.
         */
        ContributionBinding provideSetOfTypedReleasableReferenceManagers(DeclaredType metadataType) {
            return provideSetOfReleasableReferenceManagers()
                    .toBuilder()
                    .key(keyFactory.forSetOfTypedReleasableReferenceManagers(metadataType))
                    .build();
        }

        /**
         * Returns a synthetic binding for an {@linkplain BindsOptionalOf optional binding} in a
         * component with no binding for the underlying key.
         */
        ProvisionBinding syntheticAbsentBinding(Key key) {
            return ProvisionBinding.builder()
                    .contributionType(ContributionType.UNIQUE)
                    .key(key)
                    .bindingKind(Kind.SYNTHETIC_OPTIONAL_BINDING)
                    .build();
        }

        /**
         * Returns a synthetic binding for an {@linkplain BindsOptionalOf optional binding} in a
         * component with a binding for the underlying key.
         */
        ProvisionBinding syntheticPresentBinding(Key key) {
            return syntheticAbsentBinding(key)
                    .toBuilder()
                    .explicitDependencies(
                            dependencyRequestFactory.forSyntheticPresentOptionalBinding(
                                    key, DependencyRequest.Kind.PROVIDER))
                    .build();
        }
    }
}
