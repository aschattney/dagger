package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;

/**
 * Created by Andy on 05.05.2017.
 */
public class InjectorGenerator extends SourceFileGenerator<DI>{

    InjectorGenerator(Filer filer, Elements elements) {
        super(filer, elements);
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
        return null;
    }

}
