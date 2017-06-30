package dagger.internal.codegen;

import java.util.Optional;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.UnmodifiableIterator;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Decorator  extends SourceFileGenerator<ImmutableSet<BindingGraph>>{

    private BindingGraph.Factory factory;
    private ClassName testAppClassName;
    private TestRegistry testRegistry;
    private final AppConfig.Provider appConfigProvider;

    private Decorator(Filer filer, Elements elements, BindingGraph.Factory factory, ClassName testAppClassName, TestRegistry testRegistry, AppConfig.Provider appConfigProvider) {
        super(filer, elements);
        this.factory = factory;
        this.testAppClassName = testAppClassName;
        this.testRegistry = testRegistry;
        this.appConfigProvider = appConfigProvider;
    }

    @Override
    ClassName nameGeneratedType(ImmutableSet<BindingGraph> input) {
        return getClassName(input.stream().findFirst().get());
    }

    private ClassName getClassName(BindingGraph input) {
        return className(input);
    }

    static ClassName className(BindingGraph input) {
        final ComponentDescriptor topDescriptor = getTopDescriptor(input.componentDescriptor());
        final TypeElement topComponent = topDescriptor.componentDefinitionType();
        final String componentName = input.componentDescriptor().componentDefinitionType().getSimpleName().toString();
        return ClassName.get(topComponent).topLevelClassName().peerClass(componentName + "DecoratorImpl");
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(ImmutableSet<BindingGraph> input) {
        return Optional.of(input.stream().findFirst().get().componentDescriptor().componentDefinitionType());
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, ImmutableSet<BindingGraph> input) {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(generatedTypeName)
                .addModifiers(Modifier.PUBLIC);

        builder.addModifiers(Modifier.PUBLIC);
        builder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(testAppClassName, "app")
                .addStatement("this.app = app")
                .build());

        final BindingGraph bindingGraph = input.stream().findFirst().get();

        ComponentDescriptor topDescriptor = getTopDescriptor(bindingGraph.componentDescriptor());
        final BindingGraph parentGraph = factory.create(topDescriptor);
        final String daggerBuilderClassName = TriggerComponentInfo.resolveBuilderName(bindingGraph, parentGraph);

        builder.addField(testAppClassName, "app", Modifier.PRIVATE);
        TypeName builderClassName = ClassName.get(bindingGraph.componentDescriptor().builderSpec().get().builderDefinitionType());
        final ImmutableSet<ContributionBinding> delegateRequirements = bindingGraph.delegateRequirements();

        if (delegateRequirements.isEmpty()) {
            return Optional.empty();
        }else {
            final String componentName = bindingGraph.componentDescriptor().componentDefinitionType().getSimpleName().toString();
            TypeName interfaceName = getAccessorTypeName(ClassName.bestGuess(testAppClassName.toString()), componentName);
            builder.addSuperinterface(interfaceName);
            addDecoratorType(builder, daggerBuilderClassName, builderClassName, input, delegateRequirements, componentName);
            return Optional.of(builder);
        }
    }

    public TypeSpec.Builder getAccessorType(ClassName appClassName, BindingGraph bindingGraph) {
        final String componentName = bindingGraph.componentDescriptor().componentDefinitionType().getSimpleName().toString();
        ClassName name = getAccessorTypeName(appClassName, componentName);
        final TypeSpec.Builder interfaceBuilder = TypeSpec.interfaceBuilder(name).addModifiers(Modifier.PUBLIC);
        for (ContributionBinding contributionBinding : bindingGraph.delegateRequirements()) {
            Util.createDelegateMethod(name, interfaceBuilder, contributionBinding);
            if (!contributionBinding.dependencies().isEmpty()) {
                Util.createMockInstanceMethod(name, interfaceBuilder, contributionBinding);
            }
        }
        interfaceBuilder.addMethod(MethodSpec.methodBuilder("and")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(testAppClassName.topLevelClassName().peerClass("GraphDecorator"))
                .build());
        return interfaceBuilder;
    }

    private void addDecoratorType(TypeSpec.Builder builder, String className, TypeName builderClassName, ImmutableSet<BindingGraph> input, ImmutableSet<ContributionBinding> delegateRequirements, String componentName) {

        List<CodeBlock> statements = new ArrayList<>();
        final UnmodifiableIterator<BindingGraph> it = input.iterator();
        int counter = 0;
        while(it.hasNext()) {
            final BindingGraph bindingGraph = it.next();
            ComponentDescriptor topDescriptor = getTopDescriptor(bindingGraph.componentDescriptor());
            final BindingGraph parentGraph = factory.create(topDescriptor);
            final ClassName name = ClassName.bestGuess(TriggerComponentInfo.resolveBuilderName(bindingGraph, parentGraph));
            final ClassName testName = ClassName.bestGuess(TriggerComponentInfo.resolveTestBuilderName(bindingGraph, parentGraph));
            if (counter == 0) {
                createFieldAndMethodImplementations(builder, bindingGraph, delegateRequirements);
            }
            if (appConfigProvider.get().debug()) {
                apply(delegateRequirements, statements, CodeBlock.builder(), name);
            }
            if (appConfigProvider.get().generateExtendedComponents()) {
                applyTest(delegateRequirements, statements, CodeBlock.builder(), testName);
            }
            counter++;
        }

        statements.add(CodeBlock.of("throw new $T($S);", IllegalStateException.class, "could not resolve builder type"));

        builder.addMethod(MethodSpec.methodBuilder("and")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("return app")
                .returns(testAppClassName)
                .build());

        builder.addMethod(MethodSpec.methodBuilder("decorate")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(builderClassName, "builder")
                .addCode("$L", statements.stream().collect(CodeBlocks.joiningCodeBlocks("\n")))
                .returns(builderClassName)
                .build());

    }

    private void createFieldAndMethodImplementations(TypeSpec.Builder builder, BindingGraph bindingGraph, ImmutableSet<ContributionBinding> delegateRequirements) {
        TypeName interfaceName = getAccessorTypeName(ClassName.bestGuess(testAppClassName.toString()),
                bindingGraph.componentDescriptor().componentDefinitionType().getSimpleName().toString());
        for (ContributionBinding contributionBinding : delegateRequirements) {
            Util.createDelegateField(builder, contributionBinding);
            Util.createDelegateMethodImplementation(interfaceName, builder, contributionBinding);
            if (!contributionBinding.dependencies().isEmpty()) {
                Util.createMockField(builder, contributionBinding);
                Util.createMockMethodImplementation(interfaceName, builder, contributionBinding);
            }
        }
    }

    private void apply(ImmutableSet<ContributionBinding> delegateRequirements, List<CodeBlock> statements, CodeBlock.Builder codeBuilder, ClassName name) {

        delegateRequirements = ImmutableSet.copyOf(delegateRequirements.stream()
                .filter(Util::bindingSupportsTestDelegate)
                .collect(Collectors.toList()));

        if (delegateRequirements.isEmpty()) {
            return;
        }

        codeBuilder.beginControlFlow("if (builder instanceof $T)", name);
        codeBuilder.add(CodeBlock.of("$T impl = ($T) builder;\n", name, name));
        for (ContributionBinding contributionBinding : delegateRequirements) {
            final String delegateFieldName = Util.getDelegateFieldName(contributionBinding.key());
            final ClassName delegateTypeName = Util.getDelegateTypeName(contributionBinding.key());
            codeBuilder.add(CodeBlock.of("impl.$L(this.$L);\n", Util.getDelegateMethodName(delegateTypeName), delegateFieldName));
            if (!contributionBinding.dependencies().isEmpty()) {
                final String mockFieldName = Util.getMockFieldName(contributionBinding.key());
                final ClassName mockTypeName = Util.getMockTypeName(contributionBinding.key());
                codeBuilder.add(CodeBlock.of("impl.$L(this.$L);\n", Util.getMockMethodName(mockTypeName), mockFieldName));
            }
        }
        codeBuilder.add(CodeBlock.of("return impl;\n"));
        codeBuilder.endControlFlow();
        statements.add(codeBuilder.build());
    }

    private void applyTest(ImmutableSet<ContributionBinding> delegateRequirements, List<CodeBlock> statements, CodeBlock.Builder codeBuilder, ClassName name) {
        codeBuilder.beginControlFlow("if (builder instanceof $T)", name);
        codeBuilder.add(CodeBlock.of("$T impl = ($T) builder;\n", name, name));
        for (ContributionBinding contributionBinding : delegateRequirements) {
            final String delegateFieldName = Util.getDelegateFieldName(contributionBinding.key());
            final ClassName delegateTypeName = Util.getDelegateTypeName(contributionBinding.key());
            codeBuilder.add(CodeBlock.of("impl.$L(this.$L);\n", Util.getDelegateMethodName(delegateTypeName), delegateFieldName));
            if (!contributionBinding.dependencies().isEmpty()) {
                final String mockFieldName = Util.getMockFieldName(contributionBinding.key());
                final ClassName mockTypeName = Util.getMockTypeName(contributionBinding.key());
                codeBuilder.add(CodeBlock.of("impl.$L(this.$L);\n", Util.getMockMethodName(mockTypeName), mockFieldName));
            }
        }
        codeBuilder.add(CodeBlock.of("return impl;\n"));
        codeBuilder.endControlFlow();
        statements.add(codeBuilder.build());
    }

    @Override
    void generate(ImmutableSet<BindingGraph> input) throws SourceFileGenerationException {
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

    public static ClassName getAccessorTypeName(ClassName app, String componentName) {
        return app.nestedClass(componentName + "Decorator");
    }

    private static ComponentDescriptor getTopDescriptor(ComponentDescriptor descriptor) {
        while(descriptor.getParentDescriptor() != null) {
            descriptor = descriptor.getParentDescriptor();
        }
        return descriptor;
    }

    public static class Factory {

        private final Filer filer;
        private final Elements elements;
        private final BindingGraph.Factory bindingGraphFactory;
        private TestRegistry testRegistry;
        private final AppConfig.Provider appConfigProvider;

        Factory(Filer filer, Elements elements, BindingGraph.Factory bindingGraphFactory, TestRegistry testRegistry, AppConfig.Provider appConfigProvider) {
            this.filer = filer;
            this.elements = elements;
            this.bindingGraphFactory = bindingGraphFactory;
            this.testRegistry = testRegistry;
            this.appConfigProvider = appConfigProvider;
        }

        public Decorator create(ClassName testAppClassName) {
            return new Decorator(filer, elements, bindingGraphFactory, testAppClassName, testRegistry, appConfigProvider);
        }

    }

}
