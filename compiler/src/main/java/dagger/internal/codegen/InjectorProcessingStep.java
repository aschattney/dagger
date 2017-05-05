package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.Component;
import dagger.Injector;
import dagger.ProvidesComponent;

import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Andy on 05.05.2017.
 */
public class InjectorProcessingStep implements BasicAnnotationProcessor.ProcessingStep {

    private final Messager messager;
    private final InjectorGenerator injectorGenerator;
    private final ComponentDescriptor.Kind component;
    private final BindingGraph.Factory bindingGraphFactory;
    private final ComponentDescriptor.Factory componentDescriptorFactory;

    public InjectorProcessingStep(Messager messager, InjectorGenerator injectorGenerator, ComponentDescriptor.Kind component, BindingGraph.Factory bindingGraphFactory, ComponentDescriptor.Factory componentDescriptorFactory) {
        this.messager = messager;
        this.injectorGenerator = injectorGenerator;
        this.component = component;
        this.bindingGraphFactory = bindingGraphFactory;
        this.componentDescriptorFactory = componentDescriptorFactory;
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotations() {
        return ImmutableSet.of(ProvidesComponent.class, Injector.class, Component.class);
    }

    @Override
    public Set<Element> process(SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
        final Iterator<Element> it = elementsByAnnotation.get(Injector.class).iterator();
        if (!it.hasNext()) {
            return ImmutableSet.of();
        }
        final Set<Element> elements = elementsByAnnotation.get(ProvidesComponent.class);
        final List<ExecutableElement> methods = elements.stream()
                .map(element -> (ExecutableElement) element)
                .collect(Collectors.toList());

        TypeElement injector = MoreElements.asType(it.next());
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
        final DI di = new DI(injector, methods, injectorTypeList);
        this.injectorGenerator.generate(di, messager);
        return rejectedElements;
    }
}
