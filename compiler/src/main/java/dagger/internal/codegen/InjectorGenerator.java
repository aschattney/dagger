package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.Filer;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Andy on 05.05.2017.
 */
public class InjectorGenerator extends SourceFileGenerator<DI>{

    private TestClassGenerator testClassGenerator;
    private final TestRegistry registry;

    InjectorGenerator(Filer filer, Elements elements, TestClassGenerator testClassGenerator, TestRegistry registry) {
        super(filer, elements);
        this.testClassGenerator = testClassGenerator;
        this.registry = registry;
    }

    @Override
    ClassName nameGeneratedType(DI input) {
        return input.getClassName();
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(DI input) {
        return Optional.of(input.getInjector());
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, DI input) {
        final TypeElement injector = input.getInjector();
        final TypeSpec.Builder builder = TypeSpec.classBuilder(generatedTypeName)
                .superclass(ClassName.get(injector))
                .addModifiers(Modifier.PUBLIC);

        Map<Key, String> delegateFieldNames = new HashMap<>();
        Map<Key, ComponentDescriptor> componentDescriptors = new HashMap<>();
        for (InjectorType injectorType : input.getInjectorTypes()) {
            final Key key = Key.builder(injectorType.getElement().asType()).build();
            componentDescriptors.put(key, injectorType.getComponentDescriptor());
        }

        final Map<TypeElement, ProvidingMethodOverrider> methods = input.getMethods();
        for (Map.Entry<TypeElement, ProvidingMethodOverrider> entry : methods.entrySet()) {
            final ProvidingMethodOverrider overrider = entry.getValue();
            overrider.process(builder, generatedTypeName, delegateFieldNames);
        }

        return Optional.of(builder);
    }

    @Override
    void generate(DI input) throws SourceFileGenerationException {
        final Optional<TypeSpec.Builder> builder = write(input.getClassName(), input);
        try {
            registry.addEncodedClass(input.getClassName(), buildJavaFile(input.getClassName(), builder.get()));
            testClassGenerator.generate(registry);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

    }

    public static class Factory {

        private final Filer filer;
        private final Elements elements;
        private final TestClassGenerator generator;
        private TestRegistry registry;

        public Factory(Filer filer, Elements elements, TestClassGenerator generator, TestRegistry registry) {
            this.filer = filer;
            this.elements = elements;
            this.generator = generator;
            this.registry = registry;
        }

        public InjectorGenerator create() {
            return new InjectorGenerator(filer, elements, generator, registry);
        }

    }

}