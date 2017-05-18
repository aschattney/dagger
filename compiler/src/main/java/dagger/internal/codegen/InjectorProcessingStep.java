package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.TypeName;
import dagger.*;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
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
    private final InjectorGenerator injectorGenerator;
    private final ComponentDescriptor.Kind component;
    private final BindingGraph.Factory bindingGraphFactory;
    private final ComponentDescriptor.Factory componentDescriptorFactory;
    private DependencySpecGenerator dependencySpecGenerator;
    private DependencyInjectorGenerator dependencyInjectorGenerator;
    private ApplicationGenerator applicationGenerator;
    private Map<TypeElement, ExecutableElement> componentMethodMap;
    private Map<TypeElement, ExecutableElement> moduleMethodMap;
    private Map<TypeElement, ExecutableElement> subcomponentMethodMap;
    private HashSet<TypeElement> components = new HashSet<>();
    private TypeElement appClass;
    private static boolean done = false;

    public InjectorProcessingStep(Types types, Messager messager,
                                  InjectorGenerator injectorGenerator,
                                  ComponentDescriptor.Kind component, BindingGraph.Factory bindingGraphFactory,
                                  ComponentDescriptor.Factory componentDescriptorFactory,
                                  DependencySpecGenerator dependencySpecGenerator,
                                  DependencyInjectorGenerator dependencyInjectorGenerator,
                                  ApplicationGenerator applicationGenerator) {
        this.types = types;
        this.messager = messager;
        this.injectorGenerator = injectorGenerator;
        this.component = component;
        this.bindingGraphFactory = bindingGraphFactory;
        this.componentDescriptorFactory = componentDescriptorFactory;
        this.dependencySpecGenerator = dependencySpecGenerator;
        this.dependencyInjectorGenerator = dependencyInjectorGenerator;
        this.applicationGenerator = applicationGenerator;
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotations() {
        return ImmutableSet.of(Config.class, Component.class);
    }

    @Override
    public Set<Element> process(SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {

        if (done) {
            return new HashSet<>();
        }

        done = true;

        final Iterator<Element> it = elementsByAnnotation.get(Config.class).iterator();
        if (!it.hasNext()) {
            return ImmutableSet.of();
        }

        if (appClass == null) {
            try {
                it.next().getAnnotation(Config.class).applicationClass();
            } catch (MirroredTypeException e) {
                try {
                    appClass = MoreTypes.asTypeElement(e.getTypeMirror());
                } catch (TypeNotPresentException ex) {
                    throw new IllegalStateException("application class could not be extracted: " + ex.toString());
                }
            } catch (Exception e) {
                throw new IllegalStateException("application class could not be extracted");
            }
        }

        Set<Element> rejectedElements = new LinkedHashSet<>();

        components.addAll(elementsByAnnotation.get(Component.class).stream()
                .map(element -> (TypeElement) element).collect(Collectors.toSet()));

            /*this.moduleMethodMap =
                    this.buildProvideMethodMap(elementsByAnnotation.get(ProvidesModule.class));

            this.subcomponentMethodMap =
                    this.buildProvideMethodMap(elementsByAnnotation.get(ProvidesSubcomponent.class));

            this.componentMethodMap =
                    this.buildProvideMethodMap(elementsByAnnotation.get(ProvidesComponent.class));*/

/*        final Map<TypeElement, ProvidingMethodOverrider> componentOverriderMap =
                this.buildProvideComponentMethodMap(elementsByAnnotation.get(ProvidesComponent.class), injector);*/

        List<InjectorType> injectorTypeList = new ArrayList<>();
        for (Element element : elementsByAnnotation.get(component.annotationType())) {
            TypeElement componentTypeElement = (TypeElement) element;
            ComponentDescriptor componentDescriptor = componentDescriptorFactory.forComponent(componentTypeElement);
            BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
            InjectorType injectorType = new InjectorType(componentTypeElement, bindingGraph, componentDescriptor);
            injectorTypeList.add(injectorType);
        }
        if (rejectedElements.isEmpty()) {
            final DI di = new DI(appClass, components, injectorTypeList);
            this.applicationGenerator.generate(di, messager);
            this.dependencyInjectorGenerator.generate(di, messager);
            this.dependencySpecGenerator.generate(components, messager);
            this.injectorGenerator.generate(di, messager);
        } else {
            throw new IllegalStateException(rejectedElements.toString());
        }

        return rejectedElements;
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
