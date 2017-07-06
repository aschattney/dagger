package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.ClassName;
import dagger.*;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

public class InjectorProcessingStep implements BasicProcessor.ProcessingStep {

    private Types types;
    private final Messager messager;
    private TestRegistry testRegistry;
    private final AppConfig.Provider appConfigProvider;
    private final TestClassGenerator.Factory testClassGeneratorFactory;
    private final InjectorGenerator injectorGenerator;
    private final ComponentDescriptor.Kind component;
    private final BindingGraph.Factory bindingGraphFactory;
    private final ComponentDescriptor.Factory componentDescriptorFactory;
    private DependencySpecGenerator dependencySpecGenerator;
    private ProvisionBinding.Factory provisionBindingFactory;
    private ApplicationGenerator applicationGenerator;
    private StubGenerator stubGenerator;
    private HashSet<TypeElement> components = new HashSet<>();
    private DecoratorGenerator decoratorGenerator;
    private AnnotationGenerator annotationGenerator;

    public InjectorProcessingStep(Types types, Messager messager,
                                  AppConfig.Provider appConfigProvider,
                                  TestRegistry testRegistry,
                                  TestClassGenerator.Factory testClassGeneratorFactory,
                                  InjectorGenerator injectorGenerator,
                                  ComponentDescriptor.Kind component, BindingGraph.Factory bindingGraphFactory,
                                  ComponentDescriptor.Factory componentDescriptorFactory,
                                  DecoratorGenerator decoratorGenerator,
                                  DependencySpecGenerator dependencySpecGenerator,
                                  ProvisionBinding.Factory provisionBindingFactory,
                                  ApplicationGenerator applicationGenerator,
                                  StubGenerator stubGenerator,
                                  AnnotationGenerator annotationGenerator) {
        this.types = types;
        this.messager = messager;
        this.appConfigProvider = appConfigProvider;
        this.testRegistry = testRegistry;
        this.testClassGeneratorFactory = testClassGeneratorFactory;
        this.injectorGenerator = injectorGenerator;
        this.component = component;
        this.bindingGraphFactory = bindingGraphFactory;
        this.componentDescriptorFactory = componentDescriptorFactory;
        this.dependencySpecGenerator = dependencySpecGenerator;
        this.provisionBindingFactory = provisionBindingFactory;
        this.applicationGenerator = applicationGenerator;
        this.stubGenerator = stubGenerator;
        this.decoratorGenerator = decoratorGenerator;
        this.annotationGenerator = annotationGenerator;
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotations() {
        return ImmutableSet.of(Component.class);
    }

    @Override
    public Set<Element> process(SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation, boolean anyElementsRejected) {

        if(anyElementsRejected) {
            return ImmutableSet.copyOf(elementsByAnnotation.values());
        }

        final AppConfig appConfig = appConfigProvider.get();

        Set<Element> rejectedElements = new LinkedHashSet<>();

        components.addAll(elementsByAnnotation.get(Component.class).stream()
                .map(element -> (TypeElement) element).collect(Collectors.toSet()));

        List<InjectorType> injectorTypeList = new ArrayList<>();
        for (Element element : elementsByAnnotation.get(component.annotationType())) {
            TypeElement componentTypeElement = (TypeElement) element;
            ComponentDescriptor componentDescriptor = componentDescriptorFactory.forComponent(componentTypeElement);
            BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
            InjectorType injectorType = new InjectorType(componentTypeElement, bindingGraph, componentDescriptor);
            injectorTypeList.add(injectorType);
        }
        if (rejectedElements.isEmpty()) {
            for (TypeElement component : components) {
                final ComponentDescriptor componentDescriptor = componentDescriptorFactory.forComponent(component);
                final BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
                this.createAnnotations(bindingGraph);
                final ImmutableSet<ComponentDescriptor> componentDescriptors = bindingGraph.componentDescriptors();
                componentDescriptors
                        .stream()
                        .filter(descriptor -> descriptor.builderSpec().isPresent())
                        .map(descriptor -> descriptor.builderSpec().get())
                        .map(ComponentDescriptor.BuilderSpec::requirementMethods)
                        .flatMap(Collection::stream)
                        .filter(method -> method.requirement().kind() == ComponentRequirement.Kind.BINDING)
                        .forEach(method -> {
                            stubGenerator.generate(provisionBindingFactory.forBuilderBinding(method), messager);
                        });

            }
            final ClassName decoratorClassName = ClassName.get(appConfig.getAppClass()).topLevelClassName().peerClass("GraphDecorator");
            final DI di = new DI(appConfig, components, injectorTypeList, decoratorClassName);
            this.decoratorGenerator.generate(di, messager);
            this.applicationGenerator.generate(di, messager);
            this.dependencySpecGenerator.generate(di, messager);
            this.injectorGenerator.generate(di, messager);
            if (appConfig.debug() || appConfig.generateExtendedComponents()) {
                final TestClassGenerator testClassGenerator = testClassGeneratorFactory.create(di.getAppClass());
                testClassGenerator.generate(testRegistry, messager);
            }
        } else {
            throw new IllegalStateException(rejectedElements.toString());
        }

        return rejectedElements;
    }

    private void createAnnotations(BindingGraph bindingGraph) {
        annotationGenerator.generate(bindingGraph, messager);
        for (BindingGraph graph : bindingGraph.subgraphs()) {
            this.createAnnotations(graph);
        }
    }

}
