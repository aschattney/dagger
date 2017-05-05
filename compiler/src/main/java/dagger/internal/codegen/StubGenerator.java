package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.base.Optional;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
import java.util.Set;


public class StubGenerator extends SourceFileGenerator<ProvisionBinding> {

    private final Types types;

    StubGenerator(Filer filer, Elements elements, Types types) {
        super(filer, elements);
        this.types = types;
    }

    @Override
    ClassName nameGeneratedType(ProvisionBinding input) {
        return Util.getDelegateTypeName(input);
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(ProvisionBinding input) {
        return input.bindingElement();
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, ProvisionBinding input) {
        return null;
    }
}
