package dagger.internal.codegen;

import java.util.Optional;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dagger.internal.codegen.SourceFiles.simpleVariableName;
import static dagger.internal.codegen.Util.distinctByKey;
import static dagger.internal.codegen.Util.toImmutableList;

class InjectorGenerator extends SourceFileGenerator<DI> {

    static final String METHOD_NAME_PREFIX = "";

    private Messager messager;
    private final ComponentDescriptor.Factory componentDescriptorFactory;
    private final BindingGraph.Factory bindingGraphFactory;
    private TestClassGenerator.Factory testClassGeneratorFactory;
    private final TestRegistry registry;
    private Decorator.Factory decoratorFactory;

    InjectorGenerator(Filer filer, Elements elements, Messager messager, ComponentDescriptor.Factory componentDescriptorFactory, BindingGraph.Factory bindingGraphFactory, TestClassGenerator.Factory testClassGeneratorFactoty, TestRegistry registry, Decorator.Factory decoratorFactory) {
        super(filer, elements);
        this.messager = messager;
        this.componentDescriptorFactory = componentDescriptorFactory;
        this.bindingGraphFactory = bindingGraphFactory;
        this.testClassGeneratorFactory = testClassGeneratorFactoty;
        this.registry = registry;
        this.decoratorFactory = decoratorFactory;
    }

    @Override
    ClassName nameGeneratedType(DI input) {
        return input.getClassName();
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(DI input) {
        return Optional.empty();
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, DI input) {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(generatedTypeName).addModifiers(Modifier.PUBLIC);
        final Set<TypeElement> components = input.getComponents();
        final TypeElement appClass = input.getAppClass();
        builder.superclass(ClassName.get(appClass));
        builder.addSuperinterface(input.getDecoratorType());
        createDecoratorClasses(builder, components, appClass);
        for (TypeElement component : components) {
            final List<TriggerComponentInfo> infos =
                    ComponentInfo.forTrigger(component, componentDescriptorFactory, bindingGraphFactory, input.getAppClass().asType());
            infos.forEach(info -> info.process(builder));
        }

        return Optional.of(builder);
    }

    private void createDecoratorClasses(TypeSpec.Builder builder, Set<TypeElement> components, TypeElement appClass) {
        final ClassName appClassName = ClassName.get(appClass);
        ClassName testAppClassName = appClassName.topLevelClassName().peerClass("Test" + appClassName.simpleName());
        final Decorator decorator = decoratorFactory.create(testAppClassName, appClass.asType());

        final List<BindingGraph> graphs = components.stream()
                .map(componentDescriptorFactory::forComponent)
                .map(descriptor -> bindingGraphFactory.create(descriptor, appClass.asType()))
                .flatMap(this::flatMapAllSubgraphs)
                .filter(bindingGraph -> bindingGraph.componentDescriptor() != null && !bindingGraph.delegateRequirements().isEmpty())
                .collect(Collectors.toList());

        final ImmutableSetMultimap.Builder<String, BindingGraph> graphBuilder = ImmutableSetMultimap.builder();
        graphs.forEach(graph -> graphBuilder.put(Util.lowerCaseFirstLetter(graph.componentType().getSimpleName().toString()), graph));
        ImmutableSetMultimap<String, BindingGraph> groupedGraphs = graphBuilder.build();

        groupedGraphs.keySet().stream()
                .map(groupedGraphs::get)
                .forEach(e -> createDecoratorClass(builder, e, decorator, testAppClassName));

    }

    private Stream<BindingGraph> flatMapAllSubgraphs(BindingGraph graph) {
        return Stream.concat(
                Stream.of(graph),
                graph.subgraphs().stream().flatMap(this::flatMapAllSubgraphs));
    }

    private void createDecoratorClass(TypeSpec.Builder builder, ImmutableSet<BindingGraph> graphs,
                                      Decorator decorator, ClassName testAppClassName) {
        try {
            messager.printMessage(Diagnostic.Kind.NOTE, "-----");
            for (BindingGraph graph : graphs) {
                messager.printMessage(Diagnostic.Kind.NOTE, String.valueOf(graph.componentType().getSimpleName().toString()));
            }
            messager.printMessage(Diagnostic.Kind.NOTE, "-----");
            decorator.generate(graphs);
            final Optional<BindingGraph> e = graphs.stream().findFirst();
            if (!e.isPresent()) {
                return;
            }
            final ClassName decoratorName = decorator.nameGeneratedType(graphs);
            final String componentName = e.get().componentDescriptor().componentDefinitionType().getSimpleName().toString();
            final TypeName accessorName = Decorator.getAccessorTypeName(testAppClassName, componentName);
            final String fieldName = Util.lowerCaseFirstLetter(decoratorName.simpleName());
            final String methodName = Util.lowerCaseFirstLetter(fieldName.replaceAll("Decorator$", ""));
            final FieldSpec.Builder fieldBuilder = FieldSpec.builder(decoratorName, fieldName, Modifier.PRIVATE);
            final FieldSpec field = fieldBuilder.initializer("new $T(this)", decoratorName).build();
            builder.addField(field);
            builder.addMethod(MethodSpec.methodBuilder(methodName)
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(accessorName)
                    .addStatement("return this.$L", fieldName)
                    .build());
            builder.addType(decorator.getAccessorType(testAppClassName, e.get()).build());
        } catch (SourceFileGenerationException e) {
            throw new IllegalStateException("Exception while generating decorator: " + e);
        }
    }

    @Override
    void generate(DI input) throws SourceFileGenerationException {
        final Optional<TypeSpec.Builder> builder = write(input.getClassName(), input);
        try {
            if (builder.isPresent()) {
                registry.addEncodedClass(input.getClassName(), buildJavaFile(input.getClassName(), builder.get()));
            }
            final TestClassGenerator testClassGenerator = testClassGeneratorFactory.create(input.getAppClass());
            testClassGenerator.generate(registry);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
