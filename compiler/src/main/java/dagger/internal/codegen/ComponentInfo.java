package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import dagger.Trigger;

import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

import static dagger.internal.codegen.AbstractComponentWriter.simpleVariableName;

/**
 * Created by Andy on 12.05.2017.
 */
public abstract class ComponentInfo {

    protected final TypeElement component;
    protected final ComponentDescriptor descriptor;
    protected final BindingGraph bindingGraph;
    protected List<ComponentInfo> infos = new ArrayList<>();

    public static ComponentInfo forSpec(TypeElement component, ComponentDescriptor.Factory componentDescriptorFactory, BindingGraph.Factory bindingGraphFactory) {
        return createSpecComponentInfo(component, componentDescriptorFactory, bindingGraphFactory);
    }

    public static ComponentInfo forGenerator(TypeElement component, ComponentDescriptor.Factory componentDescriptorFactory, BindingGraph.Factory bindingGraphFactory) {
        return createGeneratorComponentInfo(component, componentDescriptorFactory, bindingGraphFactory);
    }

    public static TriggerComponentInfo forTrigger(TypeElement component, ComponentDescriptor.Factory componentDescriptorFactory, BindingGraph.Factory bindingGraphFactory) {
        return createTriggerComponentInfo(component, componentDescriptorFactory, bindingGraphFactory);
    }

    private static TriggerComponentInfo createTriggerComponentInfo(TypeElement component,
                                                         ComponentDescriptor.Factory componentDescriptorFactory,
                                                         BindingGraph.Factory bindingGraphFactory) {
        final ComponentDescriptor descriptor = componentDescriptorFactory.forComponent(component);
        final BindingGraph bindingGraph = bindingGraphFactory.create(descriptor);
        final TriggerComponentInfo componentInfo = new TriggerComponentInfo(component, descriptor, bindingGraph, bindingGraphFactory);
        for (BindingGraph subGraph : bindingGraph.subgraphs()) {
            componentInfo.add(new TriggerComponentInfo(subGraph.componentType(), subGraph.componentDescriptor(), subGraph, bindingGraphFactory));
            createTriggerSubcomponentInfo(subGraph, componentInfo, bindingGraphFactory);
        }
        return componentInfo;
    }

    private static void createTriggerSubcomponentInfo(BindingGraph bindingGraph, ComponentInfo componentMethodOverrider, BindingGraph.Factory factory) {
        for (BindingGraph subGraph : bindingGraph.subgraphs()) {
            ComponentDescriptor subcomponentDescriptor = subGraph.componentDescriptor();
            final ComponentInfo componentInfo =
                    new TriggerComponentInfo(subcomponentDescriptor.componentDefinitionType(), subcomponentDescriptor, subGraph, factory);
            componentMethodOverrider.add(componentInfo);
            createTriggerSubcomponentInfo(subGraph, componentInfo, factory);
        }
    }

    private static ComponentInfo createSpecComponentInfo(TypeElement component,
                                                         ComponentDescriptor.Factory componentDescriptorFactory,
                                                         BindingGraph.Factory bindingGraphFactory) {
        final ComponentDescriptor descriptor = componentDescriptorFactory.forComponent(component);
        final BindingGraph bindingGraph = bindingGraphFactory.create(descriptor);
        final ComponentInfo componentMethodOverrider = new SpecComponentInfo(component, descriptor, bindingGraph);
        createSpecSubcomponentInfo(descriptor, bindingGraphFactory, componentMethodOverrider);
        return componentMethodOverrider;
    }

    private static void createSpecSubcomponentInfo(ComponentDescriptor descriptor, BindingGraph.Factory bindingGraphFactory, ComponentInfo componentMethodOverrider) {
        final ImmutableSet<ComponentDescriptor> subcomponents = descriptor.subcomponents();
        for (ComponentDescriptor subcomponentDescriptor : subcomponents) {
            final BindingGraph bindingGraph = bindingGraphFactory.create(subcomponentDescriptor);
            final ComponentInfo subcomponentOverrider =
                    new SpecComponentInfo(subcomponentDescriptor.componentDefinitionType(), subcomponentDescriptor, bindingGraph);
            componentMethodOverrider.add(subcomponentOverrider);
            createSpecSubcomponentInfo(subcomponentDescriptor, bindingGraphFactory, subcomponentOverrider);
        }
    }

    private static ComponentInfo createGeneratorComponentInfo(TypeElement component,
                                                     ComponentDescriptor.Factory componentDescriptorFactory,
                                                     BindingGraph.Factory bindingGraphFactory) {
        final ComponentDescriptor descriptor = componentDescriptorFactory.forComponent(component);
        final BindingGraph bindingGraph = bindingGraphFactory.create(descriptor);
        final ComponentInfo componentInfo = new GeneratorComponentInfo(component, descriptor, bindingGraph);
        for (BindingGraph subGraph : bindingGraph.subgraphs()) {
            final ComponentDescriptor subDescriptor = subGraph.componentDescriptor();
            componentInfo.add(new GeneratorComponentInfo(subDescriptor.componentDefinitionType(), subDescriptor, subGraph));
            createGeneratorSubcomponentInfo(subGraph, componentInfo);
        }
        return componentInfo;
    }

    private static void createGeneratorSubcomponentInfo(BindingGraph bindingGraph, ComponentInfo componentInfo) {
        for (BindingGraph subGraph : bindingGraph.subgraphs()) {
            final ComponentDescriptor subDescriptor = subGraph.componentDescriptor();
            final ComponentInfo subcomponentInfo =
                    new GeneratorComponentInfo(subDescriptor.componentDefinitionType(), subDescriptor, subGraph);
            componentInfo.add(subcomponentInfo);
            createGeneratorSubcomponentInfo(subGraph, subcomponentInfo);
        }
    }

    protected ComponentInfo(TypeElement component, ComponentDescriptor descriptor, BindingGraph bindingGraph) {
        this.component = component;
        this.descriptor = descriptor;
        this.bindingGraph = bindingGraph;
    }

    public void add(ComponentInfo info) {
        this.infos.add(info);
    }

    public void process(TypeSpec.Builder builder) {
        for (ComponentInfo info : infos) {
            info.process(builder);
        }
    }

    public TypeElement getComponent() {
        return component;
    }

    public ComponentDescriptor getDescriptor() {
        return descriptor;
    }

    public BindingGraph getBindingGraph() {
        return bindingGraph;
    }


    protected ClassName getBuilderClassName(TypeElement component) {
        ClassName builderClassName;
        if (descriptor.kind() == ComponentDescriptor.Kind.COMPONENT) {
            builderClassName = Util.getDaggerComponentClassName(ClassName.get(component)).nestedClass("Builder");
        }else {
            if (descriptor.builderSpec().isPresent()) {
                builderClassName = ClassName.get(descriptor.builderSpec().get().builderDefinitionType());
            }else {
                throw new IllegalStateException("builder spec missing for: " + simpleVariableName(component));
            }
        }
        return builderClassName;
    }
}
