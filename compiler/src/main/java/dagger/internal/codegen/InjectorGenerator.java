package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import java.util.Optional;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.*;

public class InjectorGenerator extends SourceFileGenerator<DI>{

    private Types types;
    private Elements elements;
    private final ComponentDescriptor.Factory componentDescriptorFactory;
    private final BindingGraph.Factory bindingGraphFactory;
    private TestClassGenerator testClassGenerator;
    private final TestRegistry registry;
    private Decorator.Factory decoratorFactory;

    InjectorGenerator(Filer filer, Types types, Elements elements, ComponentDescriptor.Factory componentDescriptorFactory, BindingGraph.Factory bindingGraphFactory, TestClassGenerator testClassGenerator, TestRegistry registry, Decorator.Factory decoratorFactory) {
        super(filer, elements);
        this.types = types;
        this.elements = elements;
        this.componentDescriptorFactory = componentDescriptorFactory;
        this.bindingGraphFactory = bindingGraphFactory;
        this.testClassGenerator = testClassGenerator;
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
        final TypeSpec.Builder builder = TypeSpec.classBuilder(generatedTypeName)
                .addModifiers(Modifier.PUBLIC);

        final Set<TypeElement> components = input.getComponents();

        builder.superclass(ClassName.get(input.getAppClass()));

        final java.util.Optional<ExecutableElement> onCreateMethod = findOnCreateMethod(input.getAppClass());
        if (!onCreateMethod.isPresent()) {
            throw new IllegalStateException("onCreate method not found!");
        }
        final MethodSpec.Builder overriding = MethodSpec.overriding(onCreateMethod.get());
        createDecoratorClasses(builder, overriding, components, input.getAppClass());
        overriding.addStatement("super.onCreate()");

        //builder.addMethod(overriding.build());

        for (TypeElement component : components) {
            final TriggerComponentInfo componentInfo =
                    ComponentInfo.forTrigger(component, componentDescriptorFactory, bindingGraphFactory);
            componentInfo.process(builder);
        }

        return Optional.of(builder);
    }

    private void createDecoratorClasses(TypeSpec.Builder builder, MethodSpec.Builder methodBuilder, Set<TypeElement> components, TypeElement appClass) {
        for (TypeElement component : components) {
            ComponentDescriptor componentDescriptor = componentDescriptorFactory.forComponent(component);
            final BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
            createDecoratorClass(builder, methodBuilder, bindingGraph, appClass);
        }
    }

    private void createDecoratorClass(TypeSpec.Builder builder, MethodSpec.Builder methodBuilder, BindingGraph bindingGraph, TypeElement appClass) {
        final ClassName appClassName = ClassName.get(appClass);
        ClassName testAppClassName = appClassName.topLevelClassName().peerClass("Test" + appClassName.simpleName());
        final Decorator decorator = decoratorFactory.create(testAppClassName);
        try {
            decorator.generate(bindingGraph);
            final ClassName decoratorName = decorator.nameGeneratedType(bindingGraph);
            final String fieldName = Util.lowerCaseFirstLetter(decoratorName.simpleName());
            final String methodName = "decorate" + Util.capitalize(fieldName.replaceAll("Decorator$", ""));
            final FieldSpec.Builder fieldBuilder = FieldSpec.builder(decoratorName, fieldName, Modifier.PRIVATE);
            fieldBuilder.addAnnotation(AnnotationSpec.builder(ClassName.bestGuess("android.support.annotation.NonNull")).build());
            final FieldSpec field = fieldBuilder.initializer("new $T(this)", decoratorName).build();
            builder.addField(field);
            builder.addMethod(MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(decoratorName)
                    .addStatement("return this.$L", fieldName)
                    .build());
        } catch (SourceFileGenerationException e) {
            throw new IllegalStateException("Exception while generating decorator: " + e);
        }
        for (BindingGraph subGraph : bindingGraph.subgraphs()) {
            createDecoratorClass(builder, methodBuilder, subGraph, appClass);
        }
    }

    private java.util.Optional<ExecutableElement> findOnCreateMethod(TypeElement applicationClass) {
        final java.util.Optional<ExecutableElement> onCreateMethod = applicationClass.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .map(e -> (ExecutableElement) e)
                .filter(e -> e.getSimpleName().toString().equals("onCreate"))
                .findFirst();
        final com.google.common.base.Optional<DeclaredType> declaredTypeOptional = MoreTypes.nonObjectSuperclass(types, elements, MoreTypes.asDeclared(applicationClass.asType()));
        if (!onCreateMethod.isPresent() && declaredTypeOptional.isPresent()) {
            return findOnCreateMethod(MoreTypes.asTypeElement(declaredTypeOptional.get()));
        }
        else
            return onCreateMethod;
    }

    @Override
    void generate(DI input) throws SourceFileGenerationException {
        final Optional<TypeSpec.Builder> builder = write(input.getClassName(), input);
        try {
            registry.addEncodedClass(input.getClassName(), buildJavaFile(input.getClassName(), builder.get()));
            final Set<TypeElement> components = input.getComponents();
            testClassGenerator.setComponents(components);
            testClassGenerator.setInjector(input.getAppClass());
            testClassGenerator.generate(registry);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

    }

}
