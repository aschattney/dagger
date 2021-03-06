package dagger.internal.codegen;

import com.google.common.collect.ImmutableBiMap;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import java.util.List;

import static dagger.internal.codegen.AbstractComponentWriter.simpleVariableName;
import static dagger.internal.codegen.Util.lowerCaseFirstLetter;


public class TriggerComponentInfo extends ComponentInfo {

    private static final String DECORATOR = "Decorator";
    private BindingGraph bindingGraph;
    private static final String METHODNAME_DECORATE = "decorate";

    protected TriggerComponentInfo(TypeElement typeElement, ComponentDescriptor descriptor,
                                   BindingGraph bindingGraph) {
        super(typeElement, descriptor, bindingGraph);
        this.bindingGraph = bindingGraph;
    }

    @Override
    public List<String> process(TypeSpec.Builder builder) {
        List<String> ids = super.process(builder);

        if (noActionRequired(ids)) {
            return ids;
        }

        String methodName = getId();
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC);

        ClassName builderClassName = getBuilderClassName(component);
        methodBuilder.returns(builderClassName);
        ParameterSpec builderParameter = this.getBuilderParameterSpec(builderClassName);
        methodBuilder.addParameter(builderParameter);
        final String decoratorName = getDecoratorFieldName(component);
        methodBuilder.addStatement("return this.$L.$L(super.$L(builder))", decoratorName, METHODNAME_DECORATE, methodName);
        builder.addMethod(methodBuilder.build());
        ids.add(methodName);
        return ids;
    }

    protected String getDecoratorFieldName(TypeElement component) {
        return lowerCaseFirstLetter(component.getSimpleName().toString()) + DECORATOR;
    }

    protected boolean noActionRequired(List<String> ids) {
        return bindingGraph.delegateRequirements().isEmpty() || ids.contains(getId());
    }

    @Override
    protected String getId() {
        return "decorate" + descriptor.componentDefinitionType().getSimpleName().toString();
    }

    protected ParameterSpec getBuilderParameterSpec(ClassName builderClassName) {
        return ParameterSpec.builder(builderClassName, "builder").build();
    }

    public static String resolveTestBuilderName(BindingGraph graph, BindingGraph parentGraph) {

        final ComponentDescriptor descriptor = graph.componentDescriptor();
        final ImmutableBiMap<ComponentDescriptor, String> subcomponentNamesMap =
                new ComponentWriter.UniqueSubcomponentNamesGenerator(parentGraph).generate();

        if (descriptor.kind() == ComponentDescriptor.Kind.COMPONENT) {
            final ClassName daggerComponentClassName = Util.getDaggerComponentClassName(descriptor.componentDefinitionType());
            return daggerComponentClassName.packageName() + ".Test" + daggerComponentClassName.simpleName() + ".Builder";
        }else if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            return resolveTestSubcomponentBuilderName(subcomponentNamesMap, descriptor);
        }else {
            throw new IllegalStateException(String.format("Unknown component kind: %s", descriptor.kind()));
        }
    }

    protected static String resolveTestSubcomponentBuilderName(ImmutableBiMap<ComponentDescriptor, String> subcomponentNamesMap,
                                                           ComponentDescriptor descriptor) {
        final String parentClassName = internalResolveTestClassName(subcomponentNamesMap, descriptor.getParentDescriptor());
        final String subcomponentName = subcomponentNamesMap.get(descriptor);
        if (subcomponentName == null) {
            final String name = descriptor.componentDefinitionType().getQualifiedName().toString();
            throw new NullPointerException(String.format("Name for Subcomponent '%s' not found", name));
        }
        final String name = descriptor.componentDefinitionType().getSimpleName().toString();
        return parentClassName + "." + name + "Builder";
    }

    public static String resolveBuilderName(BindingGraph graph, BindingGraph parentGraph) {

        final ComponentDescriptor descriptor = graph.componentDescriptor();
        final ImmutableBiMap<ComponentDescriptor, String> subcomponentNamesMap =
                new ComponentWriter.UniqueSubcomponentNamesGenerator(parentGraph).generate();

        if (descriptor.kind() == ComponentDescriptor.Kind.COMPONENT) {
            final ClassName daggerComponentClassName = Util.getDaggerComponentClassName(descriptor.componentDefinitionType());
            return daggerComponentClassName.packageName() + "." + daggerComponentClassName.simpleName() + ".Builder";
        }else if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            return resolveSubcomponentBuilderName(subcomponentNamesMap, descriptor);
        }else {
            throw new IllegalStateException(String.format("Unknown component kind: %s", descriptor.kind()));
        }
    }

    protected static String resolveSubcomponentBuilderName(ImmutableBiMap<ComponentDescriptor, String> subcomponentNamesMap,
                                                           ComponentDescriptor descriptor) {
        final String parentClassName = internalResolveClassName(subcomponentNamesMap, descriptor.getParentDescriptor());
        final String subcomponentName = subcomponentNamesMap.get(descriptor);
        if (subcomponentName == null) {
            final String name = descriptor.componentDefinitionType().getQualifiedName().toString();
            throw new NullPointerException(String.format("Name for Subcomponent '%s' not found", name));
        }
        final String name = descriptor.componentDefinitionType().getSimpleName().toString();
        return parentClassName + "." + name + "Builder";
    }

    private static String internalResolveClassName(ImmutableBiMap<ComponentDescriptor, String> subcomponentNamesMap,
                                                   ComponentDescriptor descriptor) {
        return internalResolveClassName("", subcomponentNamesMap, descriptor);
    }

    private static String internalResolveTestClassName(ImmutableBiMap<ComponentDescriptor, String> subcomponentNamesMap,
                                                   ComponentDescriptor descriptor) {
        return internalResolveTestClassName("", subcomponentNamesMap, descriptor);
    }


    private static String internalResolveTestClassName(String parentClassName,
                                                   ImmutableBiMap<ComponentDescriptor, String> subcomponentNamesMap,
                                                   ComponentDescriptor descriptor) {
        if (descriptor.kind() == ComponentDescriptor.Kind.COMPONENT) {
            final ClassName daggerComponentClassName = Util.getDaggerComponentClassName(descriptor.componentDefinitionType());
            return testClassNameToString(daggerComponentClassName);
        }else if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            final String name = subcomponentNamesMap.get(descriptor);
            String newParentClass = builderImplString(parentClassName, name);
            final String resolvedClassName = internalResolveTestClassName(newParentClass,
                    subcomponentNamesMap, descriptor.getParentDescriptor());
            StringBuilder sb = new StringBuilder(resolvedClassName);
            sb.append(".");
            sb.append("Test");
            sb.append(name);
            sb.append("Impl");
            return sb.toString();
        }else {
            throw new IllegalStateException(String.format("Unknown component kind: %s", descriptor.kind()));
        }
    }

    private static String internalResolveClassName(String parentClassName,
                                                   ImmutableBiMap<ComponentDescriptor, String> subcomponentNamesMap,
                                                   ComponentDescriptor descriptor) {
        if (descriptor.kind() == ComponentDescriptor.Kind.COMPONENT) {
            final ClassName daggerComponentClassName = Util.getDaggerComponentClassName(descriptor.componentDefinitionType());
            return classNameToString(daggerComponentClassName);
        }else if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            final String name = subcomponentNamesMap.get(descriptor);
            String newParentClass = builderImplString(parentClassName, name);
            final String resolvedClassName = internalResolveClassName(newParentClass,
                    subcomponentNamesMap, descriptor.getParentDescriptor());
            StringBuilder sb = new StringBuilder(resolvedClassName);
            sb.append(".");
            sb.append(name);
            sb.append("Impl");
            return sb.toString();
        }else {
            throw new IllegalStateException(String.format("Unknown component kind: %s", descriptor.kind()));
        }
    }

    private static String builderImplString(String parentClassName, String subcomponentName) {
        return parentClassName + ".Test" + subcomponentName + "Impl";
    }

    private static String classNameToString(ClassName daggerComponentClassName) {
        return daggerComponentClassName.packageName() + "." + daggerComponentClassName.simpleName();
    }

    private static String testClassNameToString(ClassName daggerComponentClassName) {
        return daggerComponentClassName.packageName() + ".Test" + daggerComponentClassName.simpleName();
    }

}
