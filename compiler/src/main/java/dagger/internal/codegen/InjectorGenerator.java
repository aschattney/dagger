package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.Filer;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static dagger.internal.codegen.Util.createDelegateFieldAndMethod;

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

}
