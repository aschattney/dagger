package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.*;

import javax.lang.model.element.*;
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
    protected String getId() {
        return "decorate" + component.getSimpleName().toString();
    }

    @Override
    public List<String> process(TypeSpec.Builder builder) {
        List<String> ids = super.process(builder);

        if (ids.contains(getId())) {
            return ids;
        }

        String name = getId();
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(name)
                .addModifiers(Modifier.PUBLIC);

        methodBuilder.returns(ClassName.get(component));

        List<ParameterSpec> parameterSpecs = new ArrayList<>();
        if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            //final TypeElement component = descriptor.getParentDescriptor().componentDefinitionType();
            final TypeElement subcomponentTypeBuilder = descriptor.builderSpec().get().builderDefinitionType();
            parameterSpecs.add(ParameterSpec.builder(ClassName.get(subcomponentTypeBuilder), "builder").build());
        }

        if (descriptor.builderSpec().isPresent()) {
            for (ComponentDescriptor.BuilderRequirementMethod requirementMethod : descriptor.builderSpec().get().requirementMethods()) {
                final ComponentRequirement requirement = requirementMethod.requirement();
                final TypeElement typeElement = requirement.typeElement();
                if ((requirement.kind() == ComponentRequirement.Kind.MODULE &&
                        hasNotOnlyNoArgConstructor(typeElement, requirement.autoCreate()))
                        || requirement.kind() != ComponentRequirement.Kind.MODULE) {
                    parameterSpecs.add(ParameterSpec.builder(ClassName.get(typeElement), requirement.variableName()).build());
                }
            }
        } else {
            for (ModuleDescriptor moduleDescriptor : descriptor.modules()) {
                final TypeElement moduleElement = moduleDescriptor.moduleElement();
                if (hasNotOnlyNoArgConstructor(moduleElement, autoCreate(moduleElement))) {
                    parameterSpecs.add(ParameterSpec.builder(ClassName.get(moduleElement), simpleVariableName(moduleElement)).build());
                }
            }

            for (TypeElement typeElement : descriptor.dependencies()) {
                parameterSpecs.add(ParameterSpec.builder(ClassName.get(typeElement), simpleVariableName(typeElement)).build());
            }
        }

        methodBuilder.addParameters(parameterSpecs);

        CodeBlock builderInit = getBuilderInitStatement(descriptor, descriptor.getParentDescriptor());

        final List<CodeBlock> statementParams = new ArrayList<>();

        List<CodeBlock> moduleConstructorStatements = new ArrayList<>();

        if (descriptor.builderSpec().isPresent()) {
            for (ComponentDescriptor.BuilderRequirementMethod requirementMethod : descriptor.builderSpec().get().requirementMethods()) {
                final ComponentRequirement requirement = requirementMethod.requirement();
                final TypeElement typeElement = requirement.typeElement();
                final boolean hasNotOnlyNoArgConstructor = hasNotOnlyNoArgConstructor(typeElement, requirement.autoCreate());
                final String methodName = requirementMethod.method().getSimpleName().toString();
                if (requirement.kind() == ComponentRequirement.Kind.BINDING && requirementMethod.method().getReturnType().toString().equals(void.class.getName())) {
                    continue;
                }
                if ((requirement.kind() == ComponentRequirement.Kind.MODULE &&
                        hasNotOnlyNoArgConstructor) || requirement.kind() != ComponentRequirement.Kind.MODULE) {
                    moduleConstructorStatements.add(CodeBlock.of(".$L($L)",
                            methodName, requirement.variableName()));
                }else if (requirement.kind() == ComponentRequirement.Kind.MODULE && !hasNotOnlyNoArgConstructor) {
                    moduleConstructorStatements.add(CodeBlock.of(".$L(new $T())",
                            methodName, ClassName.get(requirement.typeElement())));
                }
            }
        } else {
            for (ModuleDescriptor moduleDescriptor : descriptor.modules()) {
                final TypeElement typeElement = moduleDescriptor.moduleElement();
                if (hasNotOnlyNoArgConstructor(typeElement, autoCreate(typeElement))) {
                    final String variableName = simpleVariableName(typeElement);
                    moduleConstructorStatements.add(CodeBlock.of(".$L($L)", variableName, variableName));
                }
            }

            for (TypeElement typeElement : descriptor.dependencies()) {
                final String variableName = simpleVariableName(typeElement);
                moduleConstructorStatements.add(CodeBlock.of(".$L($L)", variableName, variableName));
            }
        }

        CodeBlock modulesCodeBlock = moduleConstructorStatements
                .stream()
                .collect(CodeBlocks.joiningCodeBlocks(""));

        if (statementParams.isEmpty()) {
            methodBuilder.addStatement("return this.app.$L($L)$L.build()", name, builderInit, modulesCodeBlock);
        } else {
            methodBuilder.addStatement("return this.app.$L($L, $L)$L.build()",
                    name, builderInit, makeParametersCodeBlock(statementParams), modulesCodeBlock);
        }

        builder.addMethod(methodBuilder.build());
        ids.add(name);
        return ids;
    }

    private CodeBlock getBuilderInitStatement(ComponentDescriptor descriptor, ComponentDescriptor parentDescriptor) {
        if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            if (true) {
                return CodeBlock.of("$L", "builder");
            }
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
