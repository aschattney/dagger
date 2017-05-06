package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.Injector;
import dagger.internal.Preconditions;

import javax.inject.Inject;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;

/**
 * Created by Andy on 06.05.2017.
 */
public class ModuleStatement implements InitializationStatement {

    private Types types;
    private TypeElement injector;
    private ComponentDescriptor descriptor;
    private Map<TypeElement, ExecutableElement> moduleMethodMap;
    private Map<Key, VariableElement> providedParams;

    public ModuleStatement(Types types, TypeElement injector, ComponentDescriptor descriptor, Map<TypeElement, ExecutableElement> moduleMethodMap, Map<Key, VariableElement> providedParams) {
        Preconditions.checkNotNull(types, "types is null!");
        this.types = types;
        Preconditions.checkNotNull(injector, "injector is null!");
        this.injector = injector;
        Preconditions.checkNotNull(descriptor, "descriptor is null!");
        this.descriptor = descriptor;
        Preconditions.checkNotNull(moduleMethodMap, "moduleMethodMap is null!");
        this.moduleMethodMap = moduleMethodMap;
        Preconditions.checkNotNull(providedParams, "providedParams is null!");
        this.providedParams = providedParams;
    }

    @Override
    public CodeBlock get() {
        final CodeBlock.Builder codeBuilder = CodeBlock.builder();
        final ImmutableSet<ModuleDescriptor> modules = descriptor.modules();
        for (ModuleDescriptor moduleDescriptor : modules) {
            final TypeElement moduleElement = moduleDescriptor.moduleElement();
            final ExecutableElement method = moduleMethodMap.get(moduleElement);
            Map<Key, VariableElement> parameterMap;
            if (method != null) {
                parameterMap = buildParameterMapWithProvidingModuleMethod(method);
            }else {
                parameterMap = getConstructorParameterMap(moduleElement);
            }
            final List<CodeBlock> arguments = new ArrayList<>();
            for (Map.Entry<Key, VariableElement> entry : parameterMap.entrySet()) {
                if (resolvesToInjectorType(entry)) {
                    arguments.add(CodeBlock.of("$L", "this"));
                } else {
                    final VariableElement variableElement = providedParams.get(entry.getKey());
                    if (variableElement == null) {
                        throw new IllegalStateException("parameter is null in providedParams in ModuleStatement for param:" + entry.getValue().asType().toString() + " in module: " + moduleElement.getSimpleName().toString());
                    }
                    arguments.add(CodeBlock.of("$L", variableElement.getSimpleName().toString()));
                }
            }

            String methodName = Util.lowerCaseFirstLetter(moduleElement.getSimpleName().toString());

            if (descriptor.builderSpec().isPresent()) {
                final ExecutableElement executableElement = descriptor.builderSpec().get().methodMap().get(moduleElement);
                methodName = executableElement.getSimpleName().toString();
            }

            if (method != null) {
                codeBuilder.add(".$L(this.$L($L))",
                        methodName,
                        method.getSimpleName().toString(),
                        makeParametersCodeBlock(arguments)
                );
            }else {
                codeBuilder.add(".$L(new $T($L))",
                        methodName,
                        ClassName.get(moduleElement),
                        makeParametersCodeBlock(arguments)
                );
            }
        }
        return codeBuilder.build();
    }

    private Map<Key, VariableElement> buildParameterMapWithProvidingModuleMethod(ExecutableElement providingModuleMethod) {
        return getMethodParameterMap(providingModuleMethod);
    }

    private boolean resolvesToInjectorType(Map.Entry<Key, VariableElement> entry) {
        final TypeMirror type = entry.getKey().type();
        return types.isAssignable(injector.asType(), type);
    }

    private Map<Key, VariableElement> getMethodParameterMap(ExecutableElement element) {
        return element.getParameters()
                .stream()
                .collect(Collectors.toMap(e -> Key.builder(e.asType()).build(), Function.identity()));
    }

    private Map<Key, VariableElement> getConstructorParameterMap(TypeElement element) {
        Map<Key, VariableElement> result = new HashMap<>();
        final List<? extends Element> enclosedElements = element.getEnclosedElements();
        for (Element enclosedElement : enclosedElements) {
            if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement executableElement = (ExecutableElement) enclosedElement;
                final List<? extends VariableElement> parameters = executableElement.getParameters();
                for (VariableElement parameter : parameters) {
                    result.put(Key.builder(parameter.asType()).build(), parameter);
                }
            }
        }
        return result;
    }
}
