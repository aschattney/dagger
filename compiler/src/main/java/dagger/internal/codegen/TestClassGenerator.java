package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.squareup.javapoet.*;
import dagger.Trigger;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import java.util.Iterator;
import java.util.UUID;

/**
 * Created by Andy on 07.05.2017.
 */
public class TestClassGenerator extends SourceFileGenerator<TestRegistry> {

    TestClassGenerator(Filer filer, Elements elements) {
        super(filer, elements);
    }

    @Override
    ClassName nameGeneratedType(TestRegistry input) {
        return input.getClassName();
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(TestRegistry input) {
        return Optional.absent();
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
        return Optional.of(builder);
    }

}