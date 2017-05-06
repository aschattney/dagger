package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.CodeBlock;
import dagger.Component;
import dagger.Subcomponent;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public class BuilderStatement implements InitializationStatement {

    private ComponentDescriptor descriptor;
    private ComponentDescriptor parentDescriptor;
    private ExecutableElement providingMethod;

    public BuilderStatement(ComponentDescriptor descriptor, ComponentDescriptor parentDescriptor, ExecutableElement providingMethod) {
        this.descriptor = descriptor;
        this.parentDescriptor = parentDescriptor;
        this.providingMethod = providingMethod;
    }

    @Override
    public CodeBlock get() {
        if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
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
                            return CodeBlock.of("$L.$L()", parameter.getSimpleName().toString(), executableElement.getSimpleName().toString());
                        }
                    }
                }
            }
        }
        return CodeBlock.of("$T.builder()", Util.getDaggerComponentClassName(descriptor.componentDefinitionType()));
    }

    private boolean isComponentOrSubcomponent(TypeElement element) {
        return MoreElements.isAnnotationPresent(element, Component.class) || MoreElements.isAnnotationPresent(element, Subcomponent.class);
    }


}
