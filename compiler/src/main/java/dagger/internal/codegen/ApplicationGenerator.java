package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.*;
import dagger.Component;

import javax.annotation.processing.Filer;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;

/**
 * Created by Andy on 18.05.2017.
 */
public class ApplicationGenerator extends SourceFileGenerator<DI>{

    private Types types;
    private Elements elements;
    private final BindingGraph.Factory bindingGraphFactory;
    private final ComponentDescriptor.Factory componentDescriptorFactory;

    public ApplicationGenerator(Filer filer, Types types, Elements elements, BindingGraph.Factory bindingGraphFactory, ComponentDescriptor.Factory componentDescriptorFactory) {
        super(filer, elements);
        this.types = types;
        this.elements = elements;
        this.bindingGraphFactory = bindingGraphFactory;
        this.componentDescriptorFactory = componentDescriptorFactory;
    }

    @Override
    ClassName nameGeneratedType(DI input) {
        return ClassName.get(input.getAppClass()).topLevelClassName().peerClass("DaggerApplication");
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(DI input) {
        return Optional.ofNullable(input.getAppClass());
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, DI di) {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(generatedTypeName);
        TypeName superclass = ClassName.bestGuess("android.app.Application");
        builder.addModifiers(Modifier.PUBLIC).superclass(superclass);
        builder.addSuperinterface(ClassName.bestGuess("injector.InjectorSpec"));
        final Set<TypeElement> components = di.getComponents();

        final ClassName injectorType = ClassName.bestGuess("injector.Injector");
        builder.addField(injectorType, "injector", Modifier.PRIVATE);

        for (TypeElement component : components) {
            final SpecComponentInfo componentInfo = ComponentInfo.forSpec(component, componentDescriptorFactory, bindingGraphFactory);
            final List<MethodSpec.Builder> methodBuilders = componentInfo.getMethods();
            for (MethodSpec.Builder methodBuilder : methodBuilders) {
                List<CodeBlock> blocks = new ArrayList<>();
                blocks.add(CodeBlock.of("$L", "return builder"));
                /*final List<ParameterSpec> parameters = methodBuilder.build().parameters;
                if (parameters.size() > 1) {
                    for (ParameterSpec parameter : parameters.subList(1, parameters.size() - 1)) {
                        blocks.add(CodeBlock.of(".$L($L)", parameter.name, parameter.name));
                    }
                }*/
                final CodeBlock collect = blocks.stream().collect(CodeBlocks.joiningCodeBlocks("\n"));
                methodBuilder.addStatement("$L", collect);
                final MethodSpec build = methodBuilder.build();
                builder.addMethod(build);
            }
        }

        final Optional<ExecutableElement> onCreateMethod = findOnCreateMethod(di.getAppClass());
        if (onCreateMethod.isPresent()) {
            final MethodSpec.Builder overriding = MethodSpec.overriding(onCreateMethod.get());
            overriding.addStatement("this.injector = new $T(this)", injectorType);
            overriding.addStatement("super.onCreate()");
            builder.addMethod(overriding.build());
        }

        builder.addMethod(MethodSpec.methodBuilder("getInjector")
                .addModifiers(Modifier.PUBLIC)
                .returns(injectorType)
                .addStatement("return this.injector")
                .build());

        return Optional.ofNullable(builder);
    }

    private java.util.Optional<ExecutableElement> findOnCreateMethod(TypeElement applicationClass) {
        final java.util.Optional<ExecutableElement> onCreateMethod = applicationClass.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .map(e -> (ExecutableElement) e)
                .filter(e -> e.getSimpleName().toString().equals("onCreate"))
                .findFirst();
        final com.google.common.base.Optional<DeclaredType> declaredTypeOptional = MoreTypes.nonObjectSuperclass(types, elements, MoreTypes.asDeclared(applicationClass.asType()));
        if (!onCreateMethod.isPresent() && declaredTypeOptional.isPresent()) {
            return findOnCreateMethod(MoreTypes.asTypeElement(declaredTypeOptional.get()));
        }
        else
            return onCreateMethod;
    }

}
