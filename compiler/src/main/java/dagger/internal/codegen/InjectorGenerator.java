package dagger.internal.codegen;

import java.util.Optional;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.*;

public class InjectorGenerator extends SourceFileGenerator<DI>{

    public static final String METHOD_NAME_PREFIX = "decorate";
    private Types types;
    private Elements elements;
    private final ComponentDescriptor.Factory componentDescriptorFactory;
    private final BindingGraph.Factory bindingGraphFactory;
    private TestClassGenerator.Factory testClassGeneratorFactory;
    private final TestRegistry registry;
    private Decorator.Factory decoratorFactory;

    InjectorGenerator(Filer filer, Types types, Elements elements, ComponentDescriptor.Factory componentDescriptorFactory, BindingGraph.Factory bindingGraphFactory, TestClassGenerator.Factory testClassGeneratorFactoty, TestRegistry registry, Decorator.Factory decoratorFactory) {
        super(filer, elements);
        this.types = types;
        this.elements = elements;
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
        createDecoratorClasses(builder, components, appClass);
        for (TypeElement component : components) {
            final TriggerComponentInfo componentInfo =
                    ComponentInfo.forTrigger(component, componentDescriptorFactory, bindingGraphFactory);
            componentInfo.process(builder);
        }

        return Optional.of(builder);
    }

    private void createDecoratorClasses(TypeSpec.Builder builder, Set<TypeElement> components, TypeElement appClass) {
        for (TypeElement component : components) {
            ComponentDescriptor componentDescriptor = componentDescriptorFactory.forComponent(component);
            final BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
            createDecoratorClass(builder, bindingGraph, appClass);
        }
    }

    private void createDecoratorClass(TypeSpec.Builder builder, BindingGraph bindingGraph, TypeElement appClass) {
        final ClassName appClassName = ClassName.get(appClass);
        ClassName testAppClassName = appClassName.topLevelClassName().peerClass("Test" + appClassName.simpleName());
        final Decorator decorator = decoratorFactory.create(testAppClassName);
        try {
            decorator.generate(bindingGraph);
            if (decorator.hasBeenGenerated()) {
                final ClassName decoratorName = decorator.nameGeneratedType(bindingGraph);
                final String componentName = bindingGraph.componentDescriptor().componentDefinitionType().getSimpleName().toString();
                final TypeName accessorName = decorator.getAccessorTypeName(testAppClassName, componentName);
                final String fieldName = Util.lowerCaseFirstLetter(decoratorName.simpleName());
                final String methodName =  METHOD_NAME_PREFIX + Util.capitalize(fieldName.replaceAll("Decorator$", ""));
                final FieldSpec.Builder fieldBuilder = FieldSpec.builder(decoratorName, fieldName, Modifier.PRIVATE);
                final FieldSpec field = fieldBuilder.initializer("new $T(this)", decoratorName).build();
                builder.addField(field);
                builder.addMethod(MethodSpec.methodBuilder(methodName)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(accessorName)
                        .addStatement("return this.$L", fieldName)
                        .build());
                builder.addType(decorator.getAccessorType(testAppClassName, bindingGraph).build());
            }
        } catch (SourceFileGenerationException e) {
            throw new IllegalStateException("Exception while generating decorator: " + e);
        }
        for (BindingGraph subGraph : bindingGraph.subgraphs()) {
            createDecoratorClass(builder, subGraph, appClass);
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
