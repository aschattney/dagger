package dagger.internal.codegen;

import com.google.common.collect.ImmutableSetMultimap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dagger.internal.codegen.SourceFiles.simpleVariableName;
import static dagger.internal.codegen.Util.distinctByKey;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

class DecoratorGenerator extends SourceFileGenerator<DI>{

    private final ComponentDescriptor.Factory componentDescriptorFactory;
    private final BindingGraph.Factory bindingGraphFactory;
    private TestRegistry testRegistry;

    DecoratorGenerator(Filer filer, Elements elements,
                       ComponentDescriptor.Factory componentDescriptorFactory,
                       BindingGraph.Factory bindingGraphFactory,
                       TestRegistry testRegistry) {
        super(filer, elements);
        this.componentDescriptorFactory = componentDescriptorFactory;
        this.bindingGraphFactory = bindingGraphFactory;
        this.testRegistry = testRegistry;
    }

    @Override
    ClassName nameGeneratedType(DI input) {
        return input.getDecoratorType();
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(DI input) {
        return Optional.empty();
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, DI input) {
        final TypeSpec.Builder builder = TypeSpec.interfaceBuilder(input.getDecoratorType())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

        final Set<TypeElement> components = input.getComponents();

       components.stream()
                .map(componentDescriptorFactory::forComponent)
                .map(bindingGraphFactory::create)
                .flatMap(this::flatMapAllSubgraphs)
                .filter(bindingGraph -> bindingGraph.componentDescriptor() != null && !bindingGraph.delegateRequirements().isEmpty())
                .filter(distinctByKey(graph -> simpleVariableName(graph.componentDescriptor().componentDefinitionType())))
                .forEach(graph -> builder.addMethod(createMethod(graph, input.getClassName())));

        /*final ImmutableSetMultimap.Builder<String, BindingGraph> graphBuilder = ImmutableSetMultimap.builder();
        graphs.forEach(graph -> graphBuilder.put(simpleVariableName(graph.componentType()), graph));
        ImmutableSetMultimap<String, BindingGraph> groupedGraphs = graphBuilder.build();*/

        /*groupedGraphs.keys().stream()
                .map(groupedGraphs::get)
                .forEach(bindingGraphs -> builder.addMethod(createMethod(bindingGraphs, input.getClassName())));*/

        //groupedGraphs.entries().forEach(entry -> builder.addMethod(createMethod(entry.getValue(), input.getClassName())));

        //graphs.forEach(graph -> builder.addMethod(createMethod(graph, input.getClassName())));

        return Optional.of(builder);
    }

    private MethodSpec createMethod(BindingGraph graph, ClassName testAppClassName) {
        final String componentName = graph.componentDescriptor().componentDefinitionType().getSimpleName().toString();
        final ClassName className = Decorator.className(graph);
        final String methodName = Util.lowerCaseFirstLetter(className.simpleName().replaceAll("Decorator$", ""));
        final TypeName accessorName = Decorator.getAccessorTypeName(testAppClassName, componentName);
        return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(accessorName)
                .build();
    }

    private Stream<BindingGraph> flatMapAllSubgraphs(BindingGraph graph) {
        return Stream.concat(
                Stream.of(graph),
                graph.subgraphs().stream().flatMap(this::flatMapAllSubgraphs));
    }

    @Override
    void generate(DI input) throws SourceFileGenerationException {
        final ClassName generatedTypeName = this.nameGeneratedType(input);
        final Optional<TypeSpec.Builder> builder = write(generatedTypeName, input);
        if (builder.isPresent()) {
            try {
                testRegistry.addEncodedClass(generatedTypeName, buildJavaFile(generatedTypeName, builder.get()));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
