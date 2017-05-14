package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
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

/**
 * Created by Andy on 07.05.2017.
 */
public class TestClassGenerator extends SourceFileGenerator<TestRegistry> {

    private Set<TypeElement> components;
    private TypeElement injector;

    TestClassGenerator(Filer filer, Elements elements) {
        super(filer, elements);
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
        final TypeSpec.Builder builder = TypeSpec.classBuilder(generatedTypeName);
        final Iterator<TestRegistry.EncodedClass> it = input.iterator();
        UniqueNameSet uniqueNameSet = new UniqueNameSet();
        while(it.hasNext()) {
            final TestRegistry.EncodedClass encodedClass = it.next();
            final String randomString = UUID.randomUUID().toString().replace("-", "_");
            final String randomMethodName = uniqueNameSet.getUniqueName("Method_" + randomString);
            builder.addMethod(MethodSpec.methodBuilder(randomMethodName)
                                .addAnnotation(AnnotationSpec.builder(Trigger.class)
                                    .addMember("value", CodeBlock.of("$S", encodedClass.encoded))
                                    .addMember("qualifiedName", CodeBlock.of("$S", encodedClass.qualifiedName))
                                    .build())
                                .build()
            );
        }

        builder.addAnnotation(AnnotationSpec.builder(Trigger.class)
            .addMember("value", CodeBlock.of("$S", "injector"))
            .addMember("qualifiedName", CodeBlock.of("$S", injector.getQualifiedName().toString()))
            .build());

        return Optional.of(builder);
    }

    public void setComponents(Set<TypeElement> components) {
        if (components == null) {
            components = new HashSet<>();
        }
        this.components = components;
    }

    public void setInjector(TypeElement injector) {
        this.injector = injector;
    }
}
