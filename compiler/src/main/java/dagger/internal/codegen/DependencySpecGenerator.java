package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.googlejavaformat.java.ModifierOrderer;
import com.squareup.javapoet.*;
import dagger.Component;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dagger.internal.codegen.AbstractComponentWriter.simpleVariableName;

public class DependencySpecGenerator extends SourceFileGenerator<Set<TypeElement>> {

    private ComponentDescriptor.Factory componentDescriptorFactory;
    private BindingGraph.Factory bindingGraphFactory;

    DependencySpecGenerator(Filer filer, Elements elements, ComponentDescriptor.Factory componentDescriptorFactory, BindingGraph.Factory bindingGraphFactory) {
        super(filer, elements);
        this.componentDescriptorFactory = componentDescriptorFactory;
        this.bindingGraphFactory = bindingGraphFactory;
    }

    @Override
    ClassName nameGeneratedType(Set<TypeElement> input) {
        return ClassName.bestGuess("injector.InjectorSpec");
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(Set<TypeElement> input) {
        return Optional.absent();
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, Set<TypeElement> input) {

        final TypeSpec.Builder builder = TypeSpec.interfaceBuilder(generatedTypeName)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

        final List<ComponentInfo> info = input
                .stream()
                .map(typeElement -> ComponentInfo.forSpec(typeElement, componentDescriptorFactory, bindingGraphFactory))
                .collect(Collectors.toList());

        for (ComponentInfo componentInfo : info) {
            componentInfo.process(builder);
        }

        builder.addMethod(MethodSpec.methodBuilder("getInjector")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(ClassName.bestGuess("injector.Injector"))
                .build());

        return Optional.of(builder);
    }

}
