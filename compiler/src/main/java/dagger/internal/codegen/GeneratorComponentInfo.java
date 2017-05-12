package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.*;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import static dagger.internal.codegen.AbstractComponentWriter.simpleVariableName;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;

public class GeneratorComponentInfo extends ComponentInfo {

    protected GeneratorComponentInfo(TypeElement component, ComponentDescriptor descriptor, BindingGraph bindingGraph) {
        super(component, descriptor, bindingGraph);
    }

    @Override
    public void process(TypeSpec.Builder builder) {
        super.process(builder);
        final String name = simpleVariableName(component);
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(name)
                .addModifiers(Modifier.PUBLIC);

        methodBuilder.returns(ClassName.get(component));

        List<ParameterSpec> parameterSpecs = new ArrayList<>();
        if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            final TypeElement component = descriptor.getParentDescriptor().componentDefinitionType();
            parameterSpecs.add(ParameterSpec.builder(ClassName.get(component), simpleVariableName(component)).build());
        }

        for (ModuleDescriptor moduleDescriptor : descriptor.modules()) {
            final TypeElement typeElement = moduleDescriptor.moduleElement();
            parameterSpecs.add(ParameterSpec.builder(ClassName.get(typeElement), simpleVariableName(typeElement)).build());
        }

        for (TypeElement typeElement: descriptor.dependencies()) {
            parameterSpecs.add(ParameterSpec.builder(ClassName.get(typeElement), simpleVariableName(typeElement)).build());
        }

        methodBuilder.addParameters(parameterSpecs);

        CodeBlock builderInit = getBuilderInitStatement(descriptor, descriptor.getParentDescriptor());

        final List<CodeBlock> statementParams = new ArrayList<>();

        for (ModuleDescriptor moduleDescriptor : descriptor.modules()) {
            final TypeElement typeElement = moduleDescriptor.moduleElement();
            statementParams.add(CodeBlock.of("$L", simpleVariableName(typeElement)));
        }

        for (TypeElement typeElement: descriptor.dependencies()) {
            statementParams.add(CodeBlock.of("$L", simpleVariableName(typeElement)));
        }

        if (statementParams.isEmpty()) {
            methodBuilder.addStatement("return this.app.$L($L).build()", name, builderInit);
        }else {
            methodBuilder.addStatement("return this.app.$L($L, $L).build()", name, builderInit, makeParametersCodeBlock(statementParams));
        }

        builder.addMethod(methodBuilder.build());
    }

    private CodeBlock getBuilderInitStatement(ComponentDescriptor descriptor, ComponentDescriptor parentDescriptor) {
        if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            final ImmutableSet<ComponentDescriptor.ComponentMethodDescriptor> componentMethodDescriptors = parentDescriptor.componentMethods();
            for (ComponentDescriptor.ComponentMethodDescriptor componentMethodDescriptor : componentMethodDescriptors) {
                final ExecutableElement executableElement = componentMethodDescriptor.methodElement();
                TypeMirror typeToSearch = descriptor.componentDefinitionType().asType();
                if (descriptor.builderSpec().isPresent()) {
                    typeToSearch = descriptor.builderSpec().get().builderDefinitionType().asType();
                }
                if (executableElement.getReturnType().toString().equals(typeToSearch.toString())) {
                    final String methodName = executableElement.getSimpleName().toString();
                    if (!descriptor.builderSpec().isPresent()) {
                        // check if method has parameters ...
                        //builderModuleStatement.setExecutableElement(executableElement);
                        //return codeBlockBuilder.add("$L.$L($L))\n", builderName, methodName, builderModuleStatement.get()).build();
                        break;
                    } else {
                        return CodeBlock.of("$L.$L()\n", simpleVariableName(parentDescriptor.componentDefinitionType()), methodName);
                    }
                }
            }
        }
        final ClassName componentClassName = Util.getDaggerComponentClassName(ClassName.get(descriptor.componentDefinitionType()));
        return CodeBlock.of("$T.builder()\n", componentClassName);
    }

}
