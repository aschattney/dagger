package dagger.internal.codegen;

import com.google.common.collect.ImmutableBiMap;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import static dagger.internal.codegen.AbstractComponentWriter.simpleVariableName;
import static dagger.internal.codegen.Util.lowerCaseFirstLetter;


public class TriggerComponentInfo extends ComponentInfo {

    private BindingGraph bindingGraph;
    private static final String METHODNAME_DECORATE = "decorate";

    protected TriggerComponentInfo(TypeElement typeElement, ComponentDescriptor descriptor,
                                   BindingGraph bindingGraph) {
        super(typeElement, descriptor, bindingGraph);
        this.bindingGraph = bindingGraph;
    }

    @Override
    public void process(TypeSpec.Builder builder) {
        super.process(builder);

        if (noActionRequired()) {
            return;
        }

        final TypeElement component = descriptor.componentDefinitionType();
        String methodName = simpleVariableName(component);
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
    }

    protected String getDecoratorFieldName(TypeElement component) {
        return lowerCaseFirstLetter(component.getSimpleName().toString()) + "Decorator";
    }

    protected boolean noActionRequired() {
        return bindingGraph.delegateRequirements().isEmpty();
    }

    protected ParameterSpec getBuilderParameterSpec(ClassName builderClassName) {
        return ParameterSpec.builder(builderClassName, "builder").build();
    }

    public static String resolveBuilderName(BindingGraph.Factory bindingGraphFactory, ComponentDescriptor descriptor) {
        if (descriptor.kind() == ComponentDescriptor.Kind.COMPONENT) {
            final ClassName daggerComponentClassName = Util.getDaggerComponentClassName(descriptor.componentDefinitionType());
            return daggerComponentClassName.packageName() + "." + daggerComponentClassName.simpleName() + ".Builder";
        }else if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            return resolveSubcomponentBuilderName(bindingGraphFactory, descriptor);
        }else {
            throw new IllegalStateException(String.format("Unknown component kind: %s", descriptor.kind()));
        }
    }

    protected static String resolveSubcomponentBuilderName(BindingGraph.Factory bindingGraphFactory, ComponentDescriptor descriptor) {
        final ComponentDescriptor parentDescriptor = descriptor.getParentDescriptor();
        final String parentClassName = internalResolveClassName(parentDescriptor, bindingGraphFactory);
        final BindingGraph parentGraph = bindingGraphFactory.create(parentDescriptor);
        final ImmutableBiMap<ComponentDescriptor, String> subcomponentNamesMap =
                new ComponentWriter.UniqueSubcomponentNamesGenerator(parentGraph).generate();
        final String subcomponentName = subcomponentNamesMap.get(descriptor);
        if (subcomponentName == null) {
            final String name = descriptor.componentDefinitionType().getQualifiedName().toString();
            throw new NullPointerException(String.format("Name for Subcomponent '%s' not found", name));
        }
        return builderImplString(parentClassName, subcomponentName);
    }

    private static String internalResolveClassName(ComponentDescriptor descriptor, BindingGraph.Factory bindingGraphFactory) {
        if (descriptor.kind() == ComponentDescriptor.Kind.COMPONENT) {
            final ClassName daggerComponentClassName = Util.getDaggerComponentClassName(descriptor.componentDefinitionType());
            return classNameToString(daggerComponentClassName);
        }else if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            return resolveSubcomponentBuilderName(bindingGraphFactory, descriptor);
        }else {
            throw new IllegalStateException(String.format("Unknown component kind: %s", descriptor.kind()));
        }
    }

    private static String builderImplString(String parentClassName, String subcomponentName) {
        return parentClassName + "." + subcomponentName + "Impl";
    }

    private static String classNameToString(ClassName daggerComponentClassName) {
        return daggerComponentClassName.packageName() + "." + daggerComponentClassName.simpleName();
    }

}
