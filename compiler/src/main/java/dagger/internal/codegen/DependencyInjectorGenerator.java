package dagger.internal.codegen;

import java.util.Optional;

import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import static dagger.internal.codegen.Util.SIMPLE_NAME_INJECTOR_APPLICATION;
import static dagger.internal.codegen.Util.TYPENAME_INJECTOR_SPEC;

public class DependencyInjectorGenerator extends SourceFileGenerator<DI> {

    public static final String APP_FIELDNAME = "app";
    private final BindingGraph.Factory bindingGraphFactory;
    private final ComponentDescriptor.Factory componentDescriptorFactory;

    DependencyInjectorGenerator(Filer filer, Elements elements, BindingGraph.Factory bindingGraphFactory, ComponentDescriptor.Factory componentDescriptorFactory) {
        super(filer, elements);
        this.bindingGraphFactory = bindingGraphFactory;
        this.componentDescriptorFactory = componentDescriptorFactory;
    }

    @Override
    ClassName nameGeneratedType(DI input) {
        return Util.TYPENAME_INJECTOR;
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(DI input) {
        return Optional.empty();
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, DI input) {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(generatedTypeName).addModifiers(Modifier.PUBLIC);
        final ClassName appType = ClassName.get(input.getAppClass()).topLevelClassName().peerClass(SIMPLE_NAME_INJECTOR_APPLICATION);
        builder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(appType, APP_FIELDNAME)
                .addStatement(String.format("this.%s = %s", APP_FIELDNAME, APP_FIELDNAME))
                .build());
        builder.addField(appType, APP_FIELDNAME, Modifier.PRIVATE);
        for (TypeElement typeElement : input.getComponents()) {
            final GeneratorComponentInfo info = ComponentInfo.forGenerator(typeElement, componentDescriptorFactory, bindingGraphFactory);
            info.process(builder);
        }

        final String st =
                String.format("passed application class must implement the interface '%s'",
                        Util.TYPENAME_INJECTOR_SPEC.toString()
                );

        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("get");
        methodBuilder.addStatement("return (($T)$L).getInjector()", TYPENAME_INJECTOR_SPEC, APP_FIELDNAME);

        builder.addMethod(methodBuilder
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ClassName.bestGuess("android.app.Application"), "app")
                .returns(Util.TYPENAME_INJECTOR)
                .build());

        return Optional.of(builder);
    }


}
