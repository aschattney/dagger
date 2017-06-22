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

import static dagger.internal.codegen.ModuleProcessingStep.moduleProcessingStep;
import static dagger.internal.codegen.ModuleProcessingStep.producerModuleProcessingStep;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.googlejavaformat.java.filer.FormattingFiler;

import java.util.Arrays;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * The annotation processor responsible for generating the classes that drive the Dagger 2.0
 * implementation.
 *
 * TODO(gak): give this some better documentation
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoService(Processor.class)
public final class ComponentProcessor extends BasicProcessor {

  private TestRegistry testRegistry = new TestRegistry();

  private Filer filer;
  private Messager messager;
  private MultipleSourceFileGenerator<ProvisionBinding> multipleGenerator;
  private InjectBindingRegistry injectBindingRegistry;
  private MembersInjectorGenerator membersInjectorGenerator;
  private AppConfig.Provider appConfigProvider;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public Set<String> getSupportedOptions() {
    return CompilerOptions.SUPPORTED_OPTIONS;
  }

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {
    messager = processingEnv.getMessager();
    Types types = processingEnv.getTypeUtils();
    Elements elements = processingEnv.getElementUtils();
    filer = new FormattingFiler(processingEnv.getFiler());
    CompilerOptions compilerOptions = CompilerOptions.create(processingEnv, elements);

    KeyFormatter keyFormatter = new KeyFormatter();
    MethodSignatureFormatter methodSignatureFormatter = new MethodSignatureFormatter(types);
    BindingDeclarationFormatter bindingDeclarationFormatter =
            new BindingDeclarationFormatter(methodSignatureFormatter, keyFormatter);
    DependencyRequestFormatter dependencyRequestFormatter =
            new DependencyRequestFormatter(types, elements);

    Key.Factory keyFactory = new Key.Factory(types, elements);

    InjectValidator injectValidator = new InjectValidator(types, elements, compilerOptions);
    InjectValidator injectValidatorWhenGeneratingCode = injectValidator.whenGeneratingCode();

    ProvidesMethodValidator providesMethodValidator = new ProvidesMethodValidator(elements, types);
    ProducesMethodValidator producesMethodValidator = new ProducesMethodValidator(elements, types);
    BindsMethodValidator bindsMethodValidator = new BindsMethodValidator(elements, types);
    MultibindsMethodValidator multibindsMethodValidator =
            new MultibindsMethodValidator(elements, types);
    BindsOptionalOfMethodValidator bindsOptionalOfMethodValidator =
            new BindsOptionalOfMethodValidator(elements, types);
    AnyBindingMethodValidator anyBindingMethodValidator =
        new AnyBindingMethodValidator(
            providesMethodValidator,
            producesMethodValidator,
            bindsMethodValidator,
            multibindsMethodValidator,
            bindsOptionalOfMethodValidator);
    ModuleValidator moduleValidator =
        new ModuleValidator(
            types,
            elements,
            anyBindingMethodValidator,
            methodSignatureFormatter);
    BuilderValidator builderValidator = new BuilderValidator(elements, types);
    ComponentValidator subcomponentValidator =
            ComponentValidator.createForSubcomponent(
                    elements, types, moduleValidator, builderValidator);
    ComponentValidator componentValidator =
        ComponentValidator.createForComponent(
            elements, types, moduleValidator, subcomponentValidator, builderValidator);
    MapKeyValidator mapKeyValidator = new MapKeyValidator(elements);

    StubGenerator stubGenerator = new StubGenerator(filer, elements, types);
    FactoryGenerator factoryGenerator = new FactoryGenerator(filer, elements, compilerOptions, injectValidatorWhenGeneratingCode);
    multipleGenerator = new MultipleSourceFileGenerator<>(filer, elements, Arrays.asList(stubGenerator, factoryGenerator));
    membersInjectorGenerator = new MembersInjectorGenerator(filer, elements, injectValidatorWhenGeneratingCode);
    ComponentGenerator.Factory componentGeneratorFactory = new ComponentGenerator.Factory(filer, elements, types, keyFactory, compilerOptions);
    ProducerFactoryGenerator producerFactoryGenerator =
            new ProducerFactoryGenerator(filer, elements, compilerOptions);
    MonitoringModuleGenerator monitoringModuleGenerator =
            new MonitoringModuleGenerator(filer, elements);
    ProductionExecutorModuleGenerator productionExecutorModuleGenerator =
            new ProductionExecutorModuleGenerator(filer, elements);

    DependencyRequest.Factory dependencyRequestFactory =
            new DependencyRequest.Factory(keyFactory);
    ProvisionBinding.Factory provisionBindingFactory =
            new ProvisionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);
    ProductionBinding.Factory productionBindingFactory =
            new ProductionBinding.Factory(types, keyFactory, dependencyRequestFactory);
    MultibindingDeclaration.Factory multibindingDeclarationFactory =
            new MultibindingDeclaration.Factory(elements, types, keyFactory);
    SubcomponentDeclaration.Factory subcomponentDeclarationFactory =
            new SubcomponentDeclaration.Factory(keyFactory);

    MembersInjectionBinding.Factory membersInjectionBindingFactory =
            new MembersInjectionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);

    DelegateDeclaration.Factory bindingDelegateDeclarationFactory =
            new DelegateDeclaration.Factory(types, keyFactory, dependencyRequestFactory);
    OptionalBindingDeclaration.Factory optionalBindingDeclarationFactory =
            new OptionalBindingDeclaration.Factory(keyFactory);

    injectBindingRegistry = new InjectBindingRegistry(
            elements,
            types,
            messager,
            injectValidator,
            keyFactory,
            provisionBindingFactory,
            membersInjectionBindingFactory,
            compilerOptions);

    ModuleDescriptor.Factory moduleDescriptorFactory =
            new ModuleDescriptor.Factory(
                    elements,
                    provisionBindingFactory,
                    productionBindingFactory,
                    multibindingDeclarationFactory,
                    bindingDelegateDeclarationFactory,
                    subcomponentDeclarationFactory,
                    optionalBindingDeclarationFactory);

    ComponentDescriptor.Factory componentDescriptorFactory = new ComponentDescriptor.Factory(
            elements, types, dependencyRequestFactory, moduleDescriptorFactory);

    appConfigProvider = new AppConfig.Provider();

    BindingGraph.Factory bindingGraphFactory = new BindingGraph.Factory(
            elements,
            injectBindingRegistry,
            keyFactory,
            provisionBindingFactory,
            productionBindingFactory,
            appConfigProvider);

    AnnotationCreatorGenerator annotationCreatorGenerator =
            new AnnotationCreatorGenerator(filer, elements);
    UnwrappedMapKeyGenerator unwrappedMapKeyGenerator =
            new UnwrappedMapKeyGenerator(filer, elements);
    CanReleaseReferencesValidator canReleaseReferencesValidator =
            new CanReleaseReferencesValidator();
    ComponentHierarchyValidator componentHierarchyValidator =
            new ComponentHierarchyValidator(compilerOptions, elements);
    BindingGraphValidator bindingGraphValidator =
            new BindingGraphValidator(
                    elements,
                    types,
                    compilerOptions,
                    injectValidatorWhenGeneratingCode,
                    injectBindingRegistry,
                    bindingDeclarationFormatter,
                    methodSignatureFormatter,
                    dependencyRequestFormatter,
                    keyFormatter,
                    keyFactory);

    return ImmutableList.of(
        new AppConfigProcessingStep(messager, new AppConfig.Validator(elements, types), new AppConfig.Factory(elements), appConfigProvider),
        new MapKeyProcessingStep(
            messager, types, mapKeyValidator, annotationCreatorGenerator, unwrappedMapKeyGenerator),
        new ForReleasableReferencesValidator(messager),
        new CanReleaseReferencesProcessingStep(
            messager, canReleaseReferencesValidator, annotationCreatorGenerator),
        new InjectProcessingStep(injectBindingRegistry),
        new MonitoringModuleProcessingStep(messager, monitoringModuleGenerator),
        new ProductionExecutorModuleProcessingStep(messager, productionExecutorModuleGenerator),
        new MultibindingAnnotationsProcessingStep(messager),
        new BindsInstanceProcessingStep(messager),
        moduleProcessingStep(messager, moduleValidator, provisionBindingFactory, multipleGenerator),
        new ComponentProcessingStep(
            ComponentDescriptor.Kind.COMPONENT,
            messager,
            componentValidator,
            subcomponentValidator,
            builderValidator,
            componentHierarchyValidator,
            bindingGraphValidator,
            componentDescriptorFactory,
            bindingGraphFactory,
            componentGeneratorFactory
        ),
        producerModuleProcessingStep(
            messager,
            moduleValidator,
            provisionBindingFactory,
            multipleGenerator,
            productionBindingFactory,
            producerFactoryGenerator),
        new ComponentProcessingStep(
            ComponentDescriptor.Kind.PRODUCTION_COMPONENT,
            messager,
            componentValidator,
            subcomponentValidator,
            builderValidator,
            componentHierarchyValidator,
            bindingGraphValidator,
            componentDescriptorFactory,
            bindingGraphFactory,
            componentGeneratorFactory),
        new BindingMethodProcessingStep(messager, anyBindingMethodValidator),
        new InjectorProcessingStep(
                types,
                messager,
                appConfigProvider,
                testRegistry,
                new InjectorGenerator(filer, elements, messager, componentDescriptorFactory,
                        bindingGraphFactory, new TestClassGenerator.Factory(filer, elements),
                        testRegistry, new Decorator.Factory(filer, elements, bindingGraphFactory, testRegistry)),
                ComponentDescriptor.Kind.COMPONENT,
                bindingGraphFactory,
                componentDescriptorFactory,
                new DecoratorGenerator(filer, elements, componentDescriptorFactory, bindingGraphFactory, testRegistry),
                new DependencySpecGenerator(filer, elements, componentDescriptorFactory, bindingGraphFactory),
                new DependencyInjectorGenerator(filer, elements, bindingGraphFactory, componentDescriptorFactory),
                provisionBindingFactory,
                new ApplicationGenerator(filer, types, elements, bindingGraphFactory, componentDescriptorFactory),
                stubGenerator
        )
    );
  }

  @Override
  protected void postRound(RoundEnvironment roundEnv) {
    if (!roundEnv.processingOver()) {
      try {
        injectBindingRegistry.generateSourcesForRequiredBindings(
            multipleGenerator, membersInjectorGenerator);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(processingEnv.getMessager());
      }
    }
  }
}
