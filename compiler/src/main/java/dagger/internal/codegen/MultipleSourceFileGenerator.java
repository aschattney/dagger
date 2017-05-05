package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import java.util.Iterator;
import java.util.List;

public class MultipleSourceFileGenerator<T> extends SourceFileGenerator<T> {

    private List<SourceFileGenerator<T>> generators;
    private SourceFileGenerator<T> current;

    MultipleSourceFileGenerator(Filer filer, Elements elements, List<SourceFileGenerator<T>> generators) {
        super(filer, elements);
        this.generators = generators;
    }

    @Override
    ClassName nameGeneratedType(T input) {
        return current.nameGeneratedType(input);
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(T input) {
        return current.getElementForErrorReporting(input);
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, T input) {
        return current.write(generatedTypeName, input);
    }

    @Override
    void generate(T input) throws SourceFileGenerationException {
        final Iterator<SourceFileGenerator<T>> it = generators.iterator();
        while(it.hasNext()) {
            current = it.next();
            current.generate(input);
        }
    }
}
