package dagger.internal.codegen;

import java.util.Optional;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Decorator  extends SourceFileGenerator<BindingGraph>{

    private BindingGraph.Factory factory;
    private ClassName appClass;
    private TestRegistry testRegistry;

    private Decorator(Filer filer, Elements elements, BindingGraph.Factory factory, ClassName appClass, TestRegistry testRegistry) {
        super(filer, elements);
        this.factory = factory;
        this.appClass = appClass;
        this.testRegistry = testRegistry;
    }

    @Override
    ClassName nameGeneratedType(BindingGraph input) {
        return getClassName(input);
    }

    private ClassName getClassName(BindingGraph input) {
        final TypeElement component = input.componentDescriptor().componentDefinitionType();
        return ClassName.get(component).topLevelClassName().peerClass(component.getSimpleName().toString() + "Decorator");
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(BindingGraph input) {
        return Optional.of(input.componentDescriptor().componentDefinitionType());
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, BindingGraph input) {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(generatedTypeName)
                .addModifiers(Modifier.PUBLIC);

        final String daggerBuilderClassName = TriggerComponentInfo.resolveClassName(factory, input.componentDescriptor());

        builder.addField(appClass, "app", Modifier.PRIVATE);

        TypeName builderClassName = ClassName.get(input.componentDescriptor().builderSpec().get().builderDefinitionType());

        addDecoratorType(builder, generatedTypeName, daggerBuilderClassName, builderClassName, input);

        return Optional.of(builder);
    }

    public TypeSpec.Builder getAccessorType(ClassName appClassName, BindingGraph bindingGraph) {
        ClassName name = this.getAccessorTypeName(appClassName, bindingGraph);
        final TypeSpec.Builder interfaceBuilder = TypeSpec.interfaceBuilder(name)
                .addModifiers(Modifier.PUBLIC);
        for (ContributionBinding contributionBinding : bindingGraph.delegateRequirements()) {
            Util.createDelegateMethod(name, interfaceBuilder, contributionBinding);
        }
        interfaceBuilder.addMethod(MethodSpec.methodBuilder("and")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(appClass)
                .build());
        return interfaceBuilder;
    }

    private void addDecoratorType(TypeSpec.Builder builder, ClassName returnType, String className, TypeName builderClassName, BindingGraph bindingGraph) {

        builder.addModifiers(Modifier.PUBLIC);
        builder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(appClass, "app")
                .addStatement("this.app = app")
                .build());

        List<CodeBlock> statements = new ArrayList<>();
        final ClassName name = ClassName.bestGuess(className);
        statements.add(CodeBlock.of("$T impl = ($T) builder;", name, name));

        TypeName interfaceName = this.getAccessorTypeName(appClass, bindingGraph);

        builder.addSuperinterface(interfaceName);

        for (ContributionBinding contributionBinding : bindingGraph.delegateRequirements()) {
            Util.createDelegateFieldAndMethod(interfaceName, builder, contributionBinding, new HashMap<>(1), true);
            final String delegateFieldName = Util.getDelegateFieldName(contributionBinding.key());
            final ClassName delegateTypeName = Util.getDelegateTypeName(contributionBinding.key());
            statements.add(CodeBlock.of("impl.$L(this.$L);", Util.getDelegateMethodName(delegateTypeName), delegateFieldName));
        }

        statements.add(CodeBlock.of("return impl;"));

        builder.addMethod(MethodSpec.methodBuilder("and")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("return app")
                .returns(appClass)
                .build());

        builder.addMethod(MethodSpec.methodBuilder("decorate")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(builderClassName, "builder")
                .addCode("$L", statements.stream().collect(CodeBlocks.joiningCodeBlocks("\n")))
                .returns(builderClassName)
                .build());



    }

    @Override
    void generate(BindingGraph input) throws SourceFileGenerationException {
        final ClassName generatedTypeName = getClassName(input);
        final Optional<TypeSpec.Builder> builder = write(generatedTypeName, input);
        try {
            testRegistry.addEncodedClass(generatedTypeName, buildJavaFile(generatedTypeName, builder.get()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public ClassName getAccessorTypeName(ClassName app, BindingGraph bindingGraph) {
        return app.nestedClass(bindingGraph.componentDescriptor().componentDefinitionType().getSimpleName() + "Accessor");
    }

    public static class Factory {

        private final Filer filer;
        private final Elements elements;
        private final BindingGraph.Factory bindingGraphFactory;
        private TestRegistry testRegistry;

        Factory(Filer filer, Elements elements, BindingGraph.Factory bindingGraphFactory, TestRegistry testRegistry) {
            this.filer = filer;
            this.elements = elements;
            this.bindingGraphFactory = bindingGraphFactory;
            this.testRegistry = testRegistry;
        }

        public Decorator create(ClassName appClass) {
            return new Decorator(filer, elements, bindingGraphFactory, appClass, testRegistry);
        }

    }

}
