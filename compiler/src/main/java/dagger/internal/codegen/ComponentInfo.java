package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import dagger.Module;
import dagger.Trigger;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dagger.internal.codegen.AbstractComponentWriter.simpleVariableName;

public abstract class ComponentInfo {

    protected final TypeElement component;
    protected final ComponentDescriptor descriptor;
    protected final BindingGraph bindingGraph;
    protected List<ComponentInfo> infos = new ArrayList<>();

    public static List<SpecComponentInfo> forSpec(TypeElement component,
                                                  ComponentDescriptor.Factory componentDescriptorFactory,
                                                  BindingGraph.Factory bindingGraphFactory,
                                                  TypeMirror application) {
        return createSpecComponentInfo(component, componentDescriptorFactory, bindingGraphFactory, application)
                .stream()
                .distinct()
                .collect(Collectors.toList());
    }

    public static List<GeneratorComponentInfo> forGenerator(TypeElement component,
                                                            ComponentDescriptor.Factory componentDescriptorFactory,
                                                            BindingGraph.Factory bindingGraphFactory,
                                                            TypeMirror application) {
        return createGeneratorComponentInfo(component, componentDescriptorFactory, bindingGraphFactory, application)
                .stream()
                .distinct()
                .collect(Collectors.toList());
    }

    public static List<TriggerComponentInfo> forTrigger(TypeElement component,
                                                        ComponentDescriptor.Factory componentDescriptorFactory,
                                                        BindingGraph.Factory bindingGraphFactory,
                                                        TypeMirror application) {
        return createTriggerComponentInfo(component, componentDescriptorFactory, bindingGraphFactory, application)
                .stream()
                .distinct()
                .collect(Collectors.toList());
    }

    private static List<TriggerComponentInfo> createTriggerComponentInfo(TypeElement component,
                                                            ComponentDescriptor.Factory componentDescriptorFactory,
                                                            BindingGraph.Factory bindingGraphFactory,
                                                            TypeMirror application) {
        List<TriggerComponentInfo> infos = new ArrayList<>();
        final ComponentDescriptor descriptor = componentDescriptorFactory.forComponent(component);
        final BindingGraph bindingGraph = bindingGraphFactory.create(descriptor, application);
        final TriggerComponentInfo componentInfo = new TriggerComponentInfo(component, descriptor, bindingGraph);
        infos.add(componentInfo);
        for (BindingGraph subGraph : bindingGraph.subgraphs()) {
            infos.add(new TriggerComponentInfo(subGraph.componentType(), subGraph.componentDescriptor(), subGraph));
            infos.addAll(createTriggerSubcomponentInfo(subGraph));
        }
        return infos;
    }

    private static List<TriggerComponentInfo> createTriggerSubcomponentInfo(BindingGraph bindingGraph) {
        List<TriggerComponentInfo> infos = new ArrayList<>();
        for (BindingGraph subGraph : bindingGraph.subgraphs()) {
            ComponentDescriptor subcomponentDescriptor = subGraph.componentDescriptor();
            final TriggerComponentInfo componentInfo =
                    new TriggerComponentInfo(subcomponentDescriptor.componentDefinitionType(), subcomponentDescriptor, subGraph);
            infos.add(componentInfo);
            infos.addAll(createTriggerSubcomponentInfo(subGraph));
        }
        return infos;
    }

    private static List<SpecComponentInfo> createSpecComponentInfo(TypeElement component,
                                                         ComponentDescriptor.Factory componentDescriptorFactory,
                                                         BindingGraph.Factory bindingGraphFactory,
                                                         TypeMirror application) {
        List<SpecComponentInfo> infos = new ArrayList<>();
        final ComponentDescriptor descriptor = componentDescriptorFactory.forComponent(component);
        final BindingGraph bindingGraph = bindingGraphFactory.create(descriptor, application);
        infos.add(new SpecComponentInfo(component, descriptor, bindingGraph));
        for (BindingGraph graph : bindingGraph.subgraphs()) {
            infos.addAll(createSpecSubcomponentInfo(graph.componentDescriptor(), graph));
        }
        return infos;
    }

    private static List<SpecComponentInfo> createSpecSubcomponentInfo(ComponentDescriptor descriptor, BindingGraph graph) {
        List<SpecComponentInfo> infos = new ArrayList<>();
        final SpecComponentInfo info = new SpecComponentInfo(descriptor.componentDefinitionType(), descriptor, graph);
        infos.add(info);
        for (BindingGraph subgraph : graph.subgraphs()) {
            infos.addAll(createSpecSubcomponentInfo(subgraph.componentDescriptor(), subgraph));
        }
        return infos;
    }

    private static List<GeneratorComponentInfo> createGeneratorComponentInfo(TypeElement component,
                                                                             ComponentDescriptor.Factory componentDescriptorFactory,
                                                                             BindingGraph.Factory bindingGraphFactory,
                                                                             TypeMirror application) {
        List<GeneratorComponentInfo> infos = new ArrayList<>();
        final ComponentDescriptor descriptor = componentDescriptorFactory.forComponent(component);
        final BindingGraph bindingGraph = bindingGraphFactory.create(descriptor, application);
        infos.add(new GeneratorComponentInfo(component, descriptor, bindingGraph));
        for (BindingGraph subGraph : bindingGraph.subgraphs()) {
            infos.addAll(createGeneratorSubcomponentInfo(subGraph.componentDescriptor(), subGraph));
        }
        return infos;
    }

    private static List<GeneratorComponentInfo> createGeneratorSubcomponentInfo(ComponentDescriptor descriptor, BindingGraph bindingGraph) {
        List<GeneratorComponentInfo> infos = new ArrayList<>();
        infos.add(new GeneratorComponentInfo(descriptor.componentDefinitionType(), descriptor, bindingGraph));
        for (BindingGraph subGraph : bindingGraph.subgraphs()) {
            infos.addAll(createGeneratorSubcomponentInfo(subGraph.componentDescriptor(), subGraph));
        }
        return infos;
    }

    protected ComponentInfo(TypeElement component, ComponentDescriptor descriptor, BindingGraph bindingGraph) {
        this.component = component;
        this.descriptor = descriptor;
        this.bindingGraph = bindingGraph;
    }

    public void add(ComponentInfo info) {
        this.infos.add(info);
    }

    protected abstract String getId();

    public List<String> process(TypeSpec.Builder builder) {
        List<String> ids = new ArrayList<>();
        for (ComponentInfo info : infos) {
            ids.addAll(info.process(builder));
        }
        return ids;
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

    protected boolean autoCreate(TypeElement moduleElement) {
        final Module moduleAnnotation = moduleElement.getAnnotation(Module.class);
        return moduleAnnotation != null && moduleAnnotation.autoCreate();
    }

    protected boolean hasNotOnlyNoArgConstructor(TypeElement typeElement, boolean autoCreate) {
        if (!autoCreate) {
            return true;
        }
        for (Element element : typeElement.getEnclosedElements()) {
            if (element.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement executableElement = (ExecutableElement) element;
                if (!executableElement.getParameters().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    protected ClassName getBuilderClassName(TypeElement component) {
        ClassName builderClassName;
        if (descriptor.kind() == ComponentDescriptor.Kind.COMPONENT) {
            if (descriptor.builderSpec().isPresent()) {
                builderClassName = ClassName.get(descriptor.builderSpec().get().builderDefinitionType());
            }else {
                builderClassName = Util.getDaggerComponentClassName(ClassName.get(component)).nestedClass("Builder");
            }
        }else {
            if (descriptor.builderSpec().isPresent()) {
                builderClassName = ClassName.get(descriptor.builderSpec().get().builderDefinitionType());
            }else {
                throw new IllegalStateException("builder spec missing for: " + simpleVariableName(component));
            }
        }
        return builderClassName;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ComponentInfo)) {
            return false;
        }
        return obj.getClass().equals(getClass()) && ((ComponentInfo) obj).getId().equals(getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }
}
