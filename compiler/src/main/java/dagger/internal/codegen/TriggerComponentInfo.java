package dagger.internal.codegen;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableCollection;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dagger.internal.codegen.AbstractComponentWriter.simpleVariableName;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.Util.bindingSupportsTestDelegate;
import static dagger.internal.codegen.Util.getDelegateTypeName;


public class TriggerComponentInfo extends ComponentInfo {

    private final BindingGraph.Factory bindingGraphFactory;

    protected TriggerComponentInfo(TypeElement typeElement, ComponentDescriptor descriptor,
                                   BindingGraph bindingGraph, BindingGraph.Factory bindingGraphFactory) {
        super(typeElement, descriptor, bindingGraph);
        this.bindingGraphFactory = bindingGraphFactory;
    }

    @Override
    public void process(TypeSpec.Builder builder) {
        super.process(builder);

        final TypeElement component = descriptor.componentDefinitionType();
        String methodName = simpleVariableName(component);
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC);

        ClassName builderClassName = getBuilderClassName(component);
        methodBuilder.returns(builderClassName);
        List<ParameterSpec> parameterSpecs = new ArrayList<>();

        ParameterSpec builderParameter = ParameterSpec.builder(builderClassName, "builder").build();

        parameterSpecs.add(builderParameter);

        for (ModuleDescriptor moduleDescriptor : descriptor.modules()) {
            final TypeElement typeElement = moduleDescriptor.moduleElement();
            parameterSpecs.add(ParameterSpec.builder(ClassName.get(typeElement), simpleVariableName(typeElement)).build());
        }

        methodBuilder.addParameters(parameterSpecs);

        final List<CodeBlock> params = parameterSpecs.stream()
                .map(parameterSpec -> CodeBlock.of("$L", parameterSpec.name))
                .collect(Collectors.toList());

        String className = resolveClassName(bindingGraphFactory, descriptor);

        final ClassName name = ClassName.bestGuess(className);
        methodBuilder.addStatement("$T componentBuilder = ($T) super.$L($L)\n",
                name, name, methodName, makeParametersCodeBlock(params));

        final String decoratorName = Util.lowerCaseFirstLetter(component.getSimpleName().toString()) + "Decorator";

        methodBuilder.addStatement("this.$L.decorate(componentBuilder)", decoratorName);

        methodBuilder.addStatement("return componentBuilder");

        builder.addMethod(methodBuilder.build());
    }

    public static String resolveClassName(BindingGraph.Factory bindingGraphFactory, ComponentDescriptor descriptor) {
        if (descriptor.kind() == ComponentDescriptor.Kind.COMPONENT) {
            final ClassName daggerComponentClassName = Util.getDaggerComponentClassName(descriptor.componentDefinitionType());
            return daggerComponentClassName.packageName() + "." + daggerComponentClassName.simpleName() + ".Builder";
        }else if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            final ComponentDescriptor parentDescriptor = descriptor.getParentDescriptor();
            final String parentClassName = resolveClassName2(parentDescriptor, bindingGraphFactory);
            final BindingGraph parentGraph = bindingGraphFactory.create(parentDescriptor);
            final ImmutableBiMap<ComponentDescriptor, String> subcomponentNamesMap =
                    new ComponentWriter.UniqueSubcomponentNamesGenerator(parentGraph).generate();
            final String s = subcomponentNamesMap.get(descriptor);
            if (s == null) {
                throw new NullPointerException("s is null | " + subcomponentNamesMap.values().toString() + "|" + descriptor.componentDefinitionType().asType().toString());
            }
            return parentClassName + "." + s + "Builder";
        }else {
            throw new IllegalStateException("unknown");
        }
    }

    public static String resolveClassName2(ComponentDescriptor descriptor, BindingGraph.Factory bindingGraphFactory) {
        if (descriptor.kind() == ComponentDescriptor.Kind.COMPONENT) {
            final ClassName daggerComponentClassName = Util.getDaggerComponentClassName(descriptor.componentDefinitionType());
            return daggerComponentClassName.packageName() + "." + daggerComponentClassName.simpleName();
        }else if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            final ComponentDescriptor parentDescriptor = descriptor.getParentDescriptor();
            final String parentClassName = resolveClassName2(parentDescriptor, bindingGraphFactory);
            final BindingGraph parentGraph = bindingGraphFactory.create(parentDescriptor);
            final ImmutableBiMap<ComponentDescriptor, String> subcomponentNamesMap =
                    new ComponentWriter.UniqueSubcomponentNamesGenerator(parentGraph).generate();
            final String s = subcomponentNamesMap.get(descriptor);
            if (s == null) {
                throw new NullPointerException("s is null | " + subcomponentNamesMap.values().toString() + "|" + descriptor.componentDefinitionType().asType().toString());
            }
            return parentClassName + "." + s + "Impl";
        }else {
            throw new IllegalStateException("unknown");
        }
    }

}
