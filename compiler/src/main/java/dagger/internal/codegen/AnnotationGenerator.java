package dagger.internal.codegen;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Optional;

public class AnnotationGenerator extends SourceFileGenerator<BindingGraph> {

    private final TestRegistry testRegistry;

    AnnotationGenerator(Filer filer, Elements elements, TestRegistry testRegistry) {
        super(filer, elements);
        this.testRegistry = testRegistry;
    }

    @Override
    ClassName nameGeneratedType(BindingGraph input) {
        final String componentName = input.componentDescriptor().componentDefinitionType().getSimpleName().toString();
        return ClassName.bestGuess("dagger.annotation.In" + componentName);
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(BindingGraph input) {
        return Optional.empty();
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, BindingGraph input) {
        final TypeSpec.Builder builder = TypeSpec.annotationBuilder(generatedTypeName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Documented.class)
                .addAnnotation(AnnotationSpec.builder(Retention.class)
                        .addMember("value", "$T.$L", RetentionPolicy.class, RetentionPolicy.RUNTIME)
                        .build());
        return Optional.of(builder);
    }

    @Override
    void generate(BindingGraph input) throws SourceFileGenerationException {
        ClassName generatedTypeName = nameGeneratedType(input);
        Optional<TypeSpec.Builder> type = write(generatedTypeName, input);
        if (!type.isPresent()) {
            return;
        }
        final ClassName className = nameGeneratedType(input);
        try {
            testRegistry.addEncodedClass(className, buildJavaFile(className, type.get()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
