package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.Injector;
import dagger.ProvidesComponent;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Created by Andy on 05.05.2017.
 */
public class InjectorProcessingStep implements BasicAnnotationProcessor.ProcessingStep {
    public InjectorProcessingStep(Messager messager, InjectorGenerator injectorGenerator, ComponentDescriptor.Kind component, BindingGraph.Factory bindingGraphFactory, ComponentDescriptor.Factory componentDescriptorFactory) {
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotations() {
        return ImmutableSet.of(ProvidesComponent.class, Injector.class);
    }

    @Override
    public Set<Element> process(SetMultimap<Class<? extends Annotation>, Element> setMultimap) {
        return null;
    }
}
