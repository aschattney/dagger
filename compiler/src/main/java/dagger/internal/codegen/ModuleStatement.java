package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
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
            if (!moduleMethodMap.keySet().contains(moduleElement))
                throw new IllegalStateException(moduleMethodMap.entrySet().toString() + " | " + moduleElement.toString());
            final ExecutableElement method = moduleMethodMap.get(moduleElement);
            if (method == null) {
                throw new IllegalStateException("method is null in moduleMethodMap in ModuleStatement");
            }
            final HashMap<String, ExecutableElement> providingMethods = Util.findProvidingMethods(types, injector);
            final ExecutableElement providingModuleMethod = providingMethods.get(moduleElement.toString());
            if (providingModuleMethod == null) {
                throw new IllegalStateException("providingModuleMethod is null in providingMethods in ModuleStatement");
            }
            //final Map<Key, VariableElement> constructorParameterMap = getConstructorParameterMap(moduleElement);
            final Map<Key, VariableElement> parameterMap = getMethodParameterMap(providingModuleMethod);
            final List<CodeBlock> arguments = new ArrayList<>();
            //if (!constructorParameterMap.isEmpty() || !providedParams.isEmpty())
            //throw new IllegalStateException(constructorParameterMap.entrySet().toString() + " | "+ providedParams.entrySet().toString());
            for (Map.Entry<Key, VariableElement> entry : parameterMap.entrySet()) {
                if (resolvesToInjectorType(entry)) {
                    arguments.add(CodeBlock.of("$L", CodeBlock.of("this")));
                }else {
                    final VariableElement variableElement = providedParams.get(entry.getKey());
                    if (variableElement == null) {
                        throw new IllegalStateException("parameter is null in providedParams in ModuleStatement");
                    }
                    arguments.add(CodeBlock.of("$L", CodeBlock.of(variableElement.getSimpleName().toString())));
                }
            }
            codeBuilder.add(".$L(this.$L($L))",
                    CodeBlock.of(method.getSimpleName().toString()),
                    CodeBlock.of(providingModuleMethod.getSimpleName().toString()),
                    makeParametersCodeBlock(arguments));
        }
        return codeBuilder.build();
    }

    private boolean resolvesToInjectorType(Map.Entry<Key, VariableElement> entry) {
        final TypeMirror type = entry.getKey().type();
        return types.isAssignable(type, injector.asType());
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
