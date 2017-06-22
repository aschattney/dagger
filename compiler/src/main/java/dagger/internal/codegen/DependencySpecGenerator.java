package dagger.internal.codegen;

import com.squareup.javapoet.*;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.*;
import java.util.stream.Collectors;
import static dagger.internal.codegen.Util.METHOD_NAME_GET_INJECTOR;
import static dagger.internal.codegen.Util.TYPENAME_INJECTOR;
import static dagger.internal.codegen.Util.TYPENAME_INJECTOR_SPEC;

public class DependencySpecGenerator extends SourceFileGenerator<DI> {

    private ComponentDescriptor.Factory componentDescriptorFactory;
    private BindingGraph.Factory bindingGraphFactory;

    DependencySpecGenerator(Filer filer, Elements elements, ComponentDescriptor.Factory componentDescriptorFactory, BindingGraph.Factory bindingGraphFactory) {
        super(filer, elements);
        this.componentDescriptorFactory = componentDescriptorFactory;
        this.bindingGraphFactory = bindingGraphFactory;
    }

    @Override
    ClassName nameGeneratedType(DI input) {
        return TYPENAME_INJECTOR_SPEC;
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(DI input) {
        return Optional.empty();
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, DI input) {

        final TypeSpec.Builder builder = TypeSpec.interfaceBuilder(generatedTypeName)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

        input.getComponents().stream()
                .flatMap(typeElement -> ComponentInfo.forSpec(typeElement, componentDescriptorFactory, bindingGraphFactory)
                .stream())
                .collect(Collectors.toList())
                .forEach(info -> info.process(builder));

        /*builder.addMethod(MethodSpec.methodBuilder(METHOD_NAME_GET_INJECTOR)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(TYPENAME_INJECTOR)
                .build());*/

        return Optional.of(builder);
    }

}
