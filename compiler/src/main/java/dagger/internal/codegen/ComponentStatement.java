package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Var;
import com.squareup.javapoet.CodeBlock;

import javax.lang.model.element.*;
import javax.lang.model.util.Types;
import java.util.*;

import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;

/**
 * Created by Andy on 06.05.2017.
 */
public class ComponentStatement implements InitializationStatement{

    private Types types;
    private TypeElement injector;
    private ComponentDescriptor.Factory factory;
    private ComponentDescriptor componentDescriptor;
    private Map<Key, VariableElement> providedParams;

    public ComponentStatement(Types types, TypeElement injector, ComponentDescriptor.Factory factory, ComponentDescriptor componentDescriptor, Map<Key, VariableElement> providedParams) {
        this.types = types;
        this.injector = injector;
        this.factory = factory;
        this.componentDescriptor = componentDescriptor;
        this.providedParams = providedParams;
    }

    @Override
    public CodeBlock get() {
        final CodeBlock.Builder builder = CodeBlock.builder();
        final ImmutableSet<TypeElement> dependencies = componentDescriptor.dependencies();
        for (TypeElement element : dependencies) {
            final ComponentDescriptor dependencyComponentDescriptor = factory.forComponent(element);
            final Optional<ComponentDescriptor.BuilderSpec> builderSpec = dependencyComponentDescriptor.builderSpec();
            String methodName = builderSpec.isPresent() ? builderSpec.get().methodMap().get(element).getSimpleName().toString() : Util.lowerCaseFirstLetter(element.getSimpleName().toString());
            final HashMap<String, ExecutableElement> providingMethods = Util.findProvidingMethods(types, injector);
            if (!providingMethods.containsKey(element.toString())) {
                throw new IllegalStateException(String.format("providing method not found for component: %s", element.getSimpleName().toString()));
            }

            final Key componentKey = Key.builder(element.asType()).build();
            if (providedParams.containsKey(componentKey)) {
                builder.add(".$L($L)", CodeBlock.of(methodName), CodeBlock.of(providedParams.get(componentKey).getSimpleName().toString()));
                continue;
            }

            final ExecutableElement executableElement = providingMethods.get(element.asType().toString());

            List<CodeBlock> arguments = new ArrayList<>();
            final List<? extends VariableElement> parameters = executableElement.getParameters();
            for (VariableElement parameter : parameters) {
                final Key key = Key.builder(parameter.asType()).build();
                if (!providedParams.containsKey(key)) {
                    throw new IllegalStateException(String.format("parameter '%s' not found", parameter.getSimpleName().toString()));
                }
                final VariableElement variableElement = providedParams.get(key);
                arguments.add(CodeBlock.of("$L", CodeBlock.of(variableElement.getSimpleName().toString())));
            }

            builder.add(".$L(this.$L($L))", CodeBlock.of(methodName), CodeBlock.of(executableElement.getSimpleName().toString()), makeParametersCodeBlock(arguments));
        }
        return builder.build();
    }

}
