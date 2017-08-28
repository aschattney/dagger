package dagger.internal.codegen;

import com.squareup.javapoet.ClassName;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dagger.internal.codegen.AbstractComponentWriter.simpleVariableName;
import static dagger.internal.codegen.Util.joinClassNames;

public class ApplicationValidator {

    private final Elements elements;
    private final AppConfig.Provider appConfigProvider;
    private final Messager messager;

    public ApplicationValidator(Elements elements, AppConfig.Provider appConfigProvider, Messager messager) {
        this.elements = elements;
        this.appConfigProvider = appConfigProvider;
        this.messager = messager;
    }

    private Stream<BindingGraph> flatMapAllSubgraphs(BindingGraph graph) {
        return Stream.concat(
                Stream.of(graph),
                graph.subgraphs().stream().flatMap(this::flatMapAllSubgraphs));
    }

    public boolean validate(BindingGraph topGraph) {
        final TypeElement appClass = appConfigProvider.get().getAppClass();
        final List<ExecutableElement> methods = appClass.getEnclosedElements()
                .stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(method -> (ExecutableElement) method)
                .filter(method -> method.getSimpleName().toString().startsWith("decorate"))
                .collect(Collectors.toList());

        final List<BindingGraph> graphs = Stream.of(topGraph)
                .flatMap(this::flatMapAllSubgraphs)
                .filter(bindingGraph -> bindingGraph.componentDescriptor() != null && !bindingGraph.delegateRequirements().isEmpty())
                .collect(Collectors.toList());

        List<String> errorMessages = new ArrayList<>();

        for (BindingGraph bindingGraph : graphs) {
            final ClassName builderClassName = getBuilderClassName(bindingGraph.componentDescriptor(), bindingGraph.componentType());
            final TypeElement builderType = elements.getTypeElement(builderClassName.packageName() + "." + Util.joinClassNames(builderClassName));
            Optional<ExecutableElement> foundMethod = Optional.empty();
            for (ExecutableElement method : methods) {
                if (method.getSimpleName().toString().equals("decorate" + bindingGraph.componentType().getSimpleName().toString())
                        && builderType.getQualifiedName().toString().endsWith(method.getReturnType().toString())
                        && method.getParameters().size() == 1
                        && builderType.getQualifiedName().toString().endsWith(method.getParameters().get(0).asType().toString())) {
                    foundMethod = Optional.of(method);
                    break;
                }
            }
            if (!foundMethod.isPresent()) {
                final String message = String.format("public %s decorate%s(%s builder) { return builder; }\n",
                        joinClassNames(builderType.asType()),
                        bindingGraph.componentType().getSimpleName().toString(),
                        joinClassNames(builderType.asType()));
                errorMessages.add(message);
            }
        }

        if (!errorMessages.isEmpty()) {
            for (String errorMessage : errorMessages) {
                messager.printMessage(Diagnostic.Kind.ERROR, errorMessage, appClass);
            }
        }

        return !errorMessages.isEmpty();

    }

    protected ClassName getBuilderClassName(ComponentDescriptor descriptor, TypeElement component) {
        ClassName builderClassName;
        if (descriptor.kind() == ComponentDescriptor.Kind.COMPONENT) {
            if (descriptor.builderSpec().isPresent()) {
                builderClassName = ClassName.get(descriptor.builderSpec().get().builderDefinitionType());
            }else {
                builderClassName = Util.getDaggerComponentClassName(ClassName.get(component)).nestedClass("Builder");
            }
        }else {
            if (descriptor.builderSpec().isPresent()) {
                builderClassName = ClassName.get(descriptor.builderSpec().get().builderDefinitionType());
            }else {
                throw new IllegalStateException("builder spec missing for: " + simpleVariableName(component));
            }
        }
        return builderClassName;
    }

}
