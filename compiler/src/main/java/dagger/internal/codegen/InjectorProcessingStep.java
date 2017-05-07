package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.*;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Andy on 05.05.2017.
 */
public class InjectorProcessingStep implements BasicAnnotationProcessor.ProcessingStep {

    private Types types;
    private final Messager messager;
    private final InjectorGenerator.Factory injectorGeneratorFactory;
    private final ComponentDescriptor.Kind component;
    private final BindingGraph.Factory bindingGraphFactory;
    private final ComponentDescriptor.Factory componentDescriptorFactory;
    private Map<TypeElement, ExecutableElement> componentMethodMap;
    private Map<TypeElement, ExecutableElement> moduleMethodMap;
    private Map<TypeElement, ExecutableElement> subcomponentMethodMap;

    public InjectorProcessingStep(Types types, Messager messager, InjectorGenerator.Factory injectorGeneratorFactory, ComponentDescriptor.Kind component, BindingGraph.Factory bindingGraphFactory, ComponentDescriptor.Factory componentDescriptorFactory) {
        this.types = types;
        this.messager = messager;
        this.injectorGeneratorFactory = injectorGeneratorFactory;
        this.component = component;
        this.bindingGraphFactory = bindingGraphFactory;
        this.componentDescriptorFactory = componentDescriptorFactory;
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotations() {
        return ImmutableSet.of(ProvidesComponent.class, ProvidesSubcomponent.class, ProvidesModule.class, Injector.class, Component.class);
    }

    @Override
    public Set<Element> process(SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
        final Iterator<Element> it = elementsByAnnotation.get(Injector.class).iterator();
        if (!it.hasNext()) {
            return ImmutableSet.of();
        }

        TypeElement injector = MoreElements.asType(it.next());

        this.moduleMethodMap =
                this.buildProvideMethodMap(elementsByAnnotation.get(ProvidesModule.class));

        this.subcomponentMethodMap =
                this.buildProvideMethodMap(elementsByAnnotation.get(ProvidesSubcomponent.class));

        this.componentMethodMap =
                this.buildProvideMethodMap(elementsByAnnotation.get(ProvidesComponent.class));

        final Map<TypeElement, ProvidingMethodOverrider> componentOverriderMap =
                this.buildProvideComponentMethodMap(elementsByAnnotation.get(ProvidesComponent.class), injector);

        Set<Element> rejectedElements = new LinkedHashSet<>();
        List<InjectorType> injectorTypeList = new ArrayList<>();
        for (Element element : elementsByAnnotation.get(component.annotationType())) {
            TypeElement componentTypeElement = MoreElements.asType(element);
            try {
                ComponentDescriptor componentDescriptor = componentDescriptorFactory.forComponent(componentTypeElement);
                BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
                InjectorType injectorType = new InjectorType(componentTypeElement, bindingGraph, componentDescriptor);
                injectorTypeList.add(injectorType);
            } catch (TypeNotPresentException e) {
                rejectedElements.add(componentTypeElement);
            }
        }
        final DI di = new DI(injector, componentOverriderMap, injectorTypeList);
        this.injectorGeneratorFactory.create().generate(di, messager);
        return rejectedElements;
    }

    private Map<TypeElement, ExecutableElement> buildProvideMethodMap(Set<Element> elements) {
        return elements.stream()
                .map(element -> (ExecutableElement) element)
                .collect(Collectors.toMap(p -> MoreTypes.asTypeElement(p.getReturnType()), Function.identity()));
    }

    private Map<TypeElement, ProvidingMethodOverrider> buildProvideComponentMethodMap(Set<Element> elements, TypeElement injector) {
        return elements.stream()
                .map(element -> this.createOverrider(element, injector))
                .collect(Collectors.toMap(p -> p.getComponent(), Function.identity()));
    }

    private ProvidingMethodOverrider createOverrider(Element element, TypeElement injector) {
        ExecutableElement executableElement = (ExecutableElement) element;
        final TypeElement component = MoreTypes.asTypeElement(executableElement.getReturnType());
        final ComponentDescriptor descriptor = componentDescriptorFactory.forComponent(component);
        final BindingGraph bindingGraph = bindingGraphFactory.create(descriptor);
        List<InitializationStatement> statements = this.createInitializationsStatements(injector, descriptor, this.moduleMethodMap, executableElement, bindingGraph);
        final ProvidingMethodOverrider componentMethodOverrider = new ProvidingMethodOverrider(component, descriptor, executableElement, statements, bindingGraph);
        findSubcomponentsToOverride(injector, descriptor, componentMethodOverrider);
        return componentMethodOverrider;
    }

    private void findSubcomponentsToOverride(TypeElement injector, ComponentDescriptor descriptor, ProvidingMethodOverrider componentMethodOverrider) {
        final ImmutableSet<ComponentDescriptor> subcomponents = descriptor.subcomponents();
        for (ComponentDescriptor subcomponentDescriptor : subcomponents) {
            final ExecutableElement e = this.subcomponentMethodMap.get(subcomponentDescriptor.componentDefinitionType());
            if (e == null) {
                continue;
            }
            final TypeElement subcomponentType = MoreTypes.asTypeElement(e.getReturnType());
            final BindingGraph bindingGraph = bindingGraphFactory.create(subcomponentDescriptor);
            List<InitializationStatement> s =
                    this.createInitializationsStatements(injector, subcomponentDescriptor, this.moduleMethodMap, e, bindingGraph);
            final ProvidingMethodOverrider subcomponentOverrider =
                    new ProvidingMethodOverrider(subcomponentType, subcomponentDescriptor, e, s, bindingGraph);
            componentMethodOverrider.add(subcomponentOverrider);
            this.findSubcomponentsToOverride(injector, subcomponentDescriptor, subcomponentOverrider);
        }
    }

    private Map<Key, VariableElement> toParameterMap(List<? extends VariableElement> parameters) {
        return parameters.stream()
                .collect(Collectors.toMap(p -> Key.builder(p.asType()).build(), Function.identity()));
    }

    private List<InitializationStatement> createInitializationsStatements(TypeElement injector,
                                                                          ComponentDescriptor componentDescriptor,
                                                                          Map<TypeElement, ExecutableElement> moduleMethodMap,
                                                                          ExecutableElement providingMethod,
                                                                          BindingGraph bindingGraph) {

        Map<Key, VariableElement> providedParams = toParameterMap(providingMethod.getParameters());
        BuilderModuleStatement builderModuleStatement =
                new BuilderModuleStatement(this.types, injector, componentDescriptor, moduleMethodMap, providedParams);
        return ImmutableList.of(
                new BuilderStatement(componentDescriptor, providingMethod, builderModuleStatement, bindingGraphFactory),
                new ComponentStatement(this.types, injector, componentDescriptorFactory, componentDescriptor, providedParams),
                new ModuleStatement(this.types, injector, componentDescriptor, moduleMethodMap, providedParams),
                new FinishBuilderStatement(componentDescriptor),
                new DelegateInitialization(componentDescriptor, bindingGraph)
        );
    }
}
