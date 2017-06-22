package dagger.internal.codegen;

import java.util.Optional;
import com.squareup.javapoet.*;
import dagger.Trigger;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public class TestClassGenerator extends SourceFileGenerator<TestRegistry> {

    private final TypeElement injector;
    private UniqueNameSet uniqueNameSet;

    TestClassGenerator(Filer filer, Elements elements, TypeElement injector) {
        super(filer, elements);
        this.injector = injector;
    }

    @Override
    ClassName nameGeneratedType(TestRegistry input) {
        return input.getClassName();
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(TestRegistry input) {
        return Optional.empty();
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, TestRegistry input) {

        if (!input.isDebug()) {
            return Optional.empty();
        }

        uniqueNameSet = new UniqueNameSet();
        final TypeSpec.Builder builder = TypeSpec.classBuilder(generatedTypeName);
        final Iterator<TestRegistry.EncodedClass> it = input.iterator();
        while(it.hasNext()) {

            final TestRegistry.EncodedClass encodedClass = it.next();
            final String randomMethodName = getRandomMethodName();
            final AnnotationSpec annotation = AnnotationSpec.builder(Trigger.class)
                    .addMember("value", CodeBlock.of("$S", encodedClass.encoded))
                    .addMember("qualifiedName", CodeBlock.of("$S", encodedClass.qualifiedName))
                    .build();

            final MethodSpec method = MethodSpec.methodBuilder(randomMethodName)
                    .addAnnotation(annotation)
                    .build();

            builder.addMethod(method);
        }

        builder.addAnnotation(AnnotationSpec.builder(Trigger.class)
            .addMember("value", CodeBlock.of("$S", "injector"))
            .addMember("qualifiedName", CodeBlock.of("$S", injector.getQualifiedName().toString()))
            .build());

        return Optional.of(builder);
    }

    private String getRandomMethodName() {
        final String randomString = UUID.randomUUID().toString().replace("-", "_");
        final String randomMethodName = uniqueNameSet.getUniqueName("Method_" + randomString);
        return randomMethodName;
    }

    public static class Factory {

        private Filer filer;
        private Elements elements;

        public Factory(Filer filer, Elements elements) {
            this.filer = filer;
            this.elements = elements;
        }

        public TestClassGenerator create(TypeElement injector) {
            return new TestClassGenerator(filer, elements, injector);
        }

    }
}
