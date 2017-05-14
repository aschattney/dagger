package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.Component;
import dagger.Subcomponent;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public class BuilderStatement implements InitializationStatement {

    private final BindingGraph.Factory graphFactory;
    private ComponentDescriptor descriptor;
    private ExecutableElement providingMethod;
    private BuilderModuleStatement builderModuleStatement;

    public BuilderStatement(ComponentDescriptor descriptor, ExecutableElement providingMethod, BuilderModuleStatement builderModuleStatement, BindingGraph.Factory graphFactory) {
        this.descriptor = descriptor;
        this.providingMethod = providingMethod;
        this.builderModuleStatement = builderModuleStatement;
        this.graphFactory = graphFactory;
    }

    @Override
    public CodeBlock get() {
        if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            CodeBlock.Builder codeBlockBuilder = CodeBlock.builder();
            final ComponentDescriptor parentDescriptor = descriptor.getParentDescriptor();
            final String subComponentClassName = resolveClassName(descriptor);
            final ClassName className = ClassName.bestGuess(subComponentClassName);
            codeBlockBuilder.add("(($T)", className);
            final List<? extends VariableElement> parameters = providingMethod.getParameters();
            for (VariableElement parameter : parameters) {
                final TypeElement element = MoreTypes.asTypeElement(parameter.asType());
                if (isComponentOrSubcomponent(element)) {
                    final ImmutableSet<ComponentDescriptor.ComponentMethodDescriptor> componentMethodDescriptors = parentDescriptor.componentMethods();
                    for (ComponentDescriptor.ComponentMethodDescriptor componentMethodDescriptor : componentMethodDescriptors) {
                        final ExecutableElement executableElement = componentMethodDescriptor.methodElement();
                        TypeMirror typeToSearch = descriptor.componentDefinitionType().asType();
                        if (descriptor.builderSpec().isPresent()) {
                            typeToSearch = descriptor.builderSpec().get().builderDefinitionType().asType();
                        }
                        if (executableElement.getReturnType().toString().equals(typeToSearch.toString())) {
                            final String parameterName = parameter.getSimpleName().toString();
                            final String methodName = executableElement.getSimpleName().toString();
                            if (!descriptor.builderSpec().isPresent() ) {
                                // check if method has parameters ...
                                builderModuleStatement.setExecutableElement(executableElement);
                                return codeBlockBuilder.add("$L.$L($L))\n", parameterName, methodName, builderModuleStatement.get()).build();
                            }else {
                                return codeBlockBuilder.add("$L.$L()\n", parameterName, methodName).build();
                            }
                        }
                    }
                }
            }
        }
        final ClassName componentClassName = Util.getDaggerComponentClassName(descriptor.componentDefinitionType());
        return CodeBlock.of("(($T)$T.builder()\n", componentClassName, componentClassName);
    }

    private String resolveClassName(ComponentDescriptor descriptor) {
        if (descriptor.kind() == ComponentDescriptor.Kind.COMPONENT) {
            final ClassName daggerComponentClassName = Util.getDaggerComponentClassName(descriptor.componentDefinitionType());
            return daggerComponentClassName.packageName() + "." + daggerComponentClassName.simpleName();
        }else if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            final String parentClassName = resolveClassName(descriptor.getParentDescriptor());
            final BindingGraph parentGraph = graphFactory.create(descriptor.getParentDescriptor());
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

    private boolean isComponentOrSubcomponent(TypeElement element) {
        return MoreElements.isAnnotationPresent(element, Component.class) || MoreElements.isAnnotationPresent(element, Subcomponent.class);
    }


}
