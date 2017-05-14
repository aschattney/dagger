package dagger.internal.codegen;

import java.util.Optional;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.List;

import static dagger.internal.codegen.AbstractComponentWriter.simpleVariableName;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;

/**
 * Created by Andy on 11.05.2017.
 */
public class DependencyInjectorGenerator extends SourceFileGenerator<DI> {

    private final BindingGraph.Factory bindingGraphFactory;
    private final ComponentDescriptor.Factory componentDescriptorFactory;

    DependencyInjectorGenerator(Filer filer, Elements elements, BindingGraph.Factory bindingGraphFactory, ComponentDescriptor.Factory componentDescriptorFactory) {
        super(filer, elements);
        this.bindingGraphFactory = bindingGraphFactory;
        this.componentDescriptorFactory = componentDescriptorFactory;
    }

    @Override
    ClassName nameGeneratedType(DI input) {
        return ClassName.bestGuess("injector.Injector");
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(DI input) {
        return Optional.empty();
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, DI input) {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(generatedTypeName).addModifiers(Modifier.PUBLIC);
        final ClassName appType = ClassName.get(input.getAppClass());
        builder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(appType, "app")
                .addStatement("this.app = app")
                .build());
        builder.addField(appType, "app", Modifier.PRIVATE);
        for (TypeElement typeElement : input.getComponents()) {
            final ComponentInfo info = ComponentInfo.forGenerator(typeElement, componentDescriptorFactory, bindingGraphFactory);
            info.process(builder);
        }
        return Optional.of(builder);
    }


}
