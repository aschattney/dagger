package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.Preconditions;

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
public class BuilderModuleStatement extends ModuleStatement implements InitializationStatement {

    private ExecutableElement executableElement;

    public BuilderModuleStatement(Types types, TypeElement injector, ComponentDescriptor descriptor, Map<TypeElement, ExecutableElement> moduleMethodMap, Map<Key, VariableElement> providedParams) {
        super(types, injector, descriptor, moduleMethodMap, providedParams);
    }

    public void setExecutableElement(ExecutableElement executableElement) {
        this.executableElement = executableElement;
    }

    @Override
    public CodeBlock get() {
        List<CodeBlock> moduleInitStatements = new ArrayList<>();
        final ImmutableSet<ModuleDescriptor> modules = descriptor.modules();

        if (isSubComponent() && !descriptor.builderSpec().isPresent()) {

            final Map<Key, ModuleDescriptor> moduleDescriptorMap = modules.stream()
                    .collect(Collectors.toMap(p -> Key.builder(p.moduleElement().asType()).build(), Function.identity()));

            final List<? extends VariableElement> parameters = executableElement.getParameters();
            for (VariableElement parameter : parameters) {
                final Key key = Key.builder(parameter.asType()).build();
                if (!moduleDescriptorMap.containsKey(key)) {
                    throw new IllegalStateException(String.format("%s | %s not found", moduleDescriptorMap.entrySet().toString(), parameter.asType().toString()));
                }
                final ModuleDescriptor moduleDescriptor = moduleDescriptorMap.get(key);
                moduleInitStatements.add(processModule(moduleDescriptor));
            }
        }

        return makeParametersCodeBlock(moduleInitStatements);
    }

    private CodeBlock processModule(ModuleDescriptor moduleDescriptor) {
        final CodeBlock.Builder codeBuilder = CodeBlock.builder();
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

        if (method != null) {
            codeBuilder.add("this.$L($L)",
                    method.getSimpleName().toString(),
                    makeParametersCodeBlock(arguments)
            );
        }else {
            codeBuilder.add("new $T($L)",
                    ClassName.get(moduleElement),
                    makeParametersCodeBlock(arguments)
            );
        }
        return codeBuilder.build();
    }

    private boolean isSubComponent() {
        return descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT;
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
