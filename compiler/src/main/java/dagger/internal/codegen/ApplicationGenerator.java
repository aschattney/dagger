package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.stream.Collectors;

import static dagger.internal.codegen.Util.*;

class ApplicationGenerator extends SourceFileGenerator<DI>{

    private static final String SUPER_ON_CREATE_CALL = "super.onCreate()";
    public static final CodeBlock CODEBLOCK_RETURN_BUILDER = CodeBlock.of("$L", "return builder");

    private Types types;
    private Elements elements;
    private final BindingGraph.Factory bindingGraphFactory;
    private final ComponentDescriptor.Factory componentDescriptorFactory;

    ApplicationGenerator(Filer filer, Types types, Elements elements, BindingGraph.Factory bindingGraphFactory, ComponentDescriptor.Factory componentDescriptorFactory) {
        super(filer, elements);
        this.types = types;
        this.elements = elements;
        this.bindingGraphFactory = bindingGraphFactory;
        this.componentDescriptorFactory = componentDescriptorFactory;
    }

    @Override
    ClassName nameGeneratedType(DI input) {
        return ClassName.get(input.getAppClass()).topLevelClassName().peerClass(Util.SIMPLE_NAME_INJECTOR_APPLICATION);
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(DI input) {
        return Optional.ofNullable(input.getAppClass());
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, DI di) {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(generatedTypeName);
        TypeName superclass = ClassName.get(di.getBaseAppClass());
        builder.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).superclass(superclass);
        builder.addSuperinterface(TYPENAME_INJECTOR_SPEC);
        final Set<TypeElement> components = di.getComponents();

        //builder.addField(TYPENAME_INJECTOR, FIELDNAME_INJECTOR, Modifier.PRIVATE);

        for (TypeElement component : components) {
            final List<SpecComponentInfo> infos = ComponentInfo.forSpec(component, componentDescriptorFactory, bindingGraphFactory, di.getAppClass().asType());
            final List<MethodSpec.Builder> methodBuilders = infos.stream()
                    .flatMap(info -> info.getMethods().stream())
                    .collect(Collectors.toList());
            List<String> methodNames = new ArrayList<>();
            final Iterator<MethodSpec.Builder> it = methodBuilders.iterator();
            while(it.hasNext()) {
                final String name = it.next().build().name;
                if (!methodNames.contains(name)) {
                    methodNames.add(name);
                }else {
                    it.remove();
                }
            }
            for (MethodSpec.Builder methodBuilder : methodBuilders) {
                List<CodeBlock> blocks = new ArrayList<>();
                blocks.add(CODEBLOCK_RETURN_BUILDER);
                final CodeBlock codeBlocks = blocks.stream().collect(CodeBlocks.joiningCodeBlocks("\n"));
                methodBuilder.addStatement("$L", codeBlocks);
                final MethodSpec build = methodBuilder.build();
                builder.addMethod(build);
            }
        }

        final Optional<ExecutableElement> onCreateMethod = findOnCreateMethod(di.getAppClass());
        if (onCreateMethod.isPresent()) {
            final MethodSpec.Builder overriding = MethodSpec.overriding(onCreateMethod.get());
            //overriding.addStatement("this.$L = new $T(this)", FIELDNAME_INJECTOR, TYPENAME_INJECTOR);
            overriding.addStatement(SUPER_ON_CREATE_CALL);
            builder.addMethod(overriding.build());
        }

        /*builder.addMethod(MethodSpec.methodBuilder(METHOD_NAME_GET_INJECTOR)
                .addModifiers(Modifier.PUBLIC)
                .returns(TYPENAME_INJECTOR)
                .addStatement(String.format("return this.%s", FIELDNAME_INJECTOR))
                .build());*/

        return Optional.ofNullable(builder);
    }

    private java.util.Optional<ExecutableElement> findOnCreateMethod(TypeElement applicationClass) {
        final java.util.Optional<ExecutableElement> onCreateMethod = applicationClass.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .map(e -> (ExecutableElement) e)
                .filter(e -> e.getSimpleName().toString().equals("onCreate") && e.getParameters().isEmpty())
                .findFirst();
        final com.google.common.base.Optional<DeclaredType> declaredTypeOptional = MoreTypes.nonObjectSuperclass(types, elements, MoreTypes.asDeclared(applicationClass.asType()));
        if (!onCreateMethod.isPresent() && declaredTypeOptional.isPresent()) {
            return findOnCreateMethod(MoreTypes.asTypeElement(declaredTypeOptional.get()));
        }
        else
            return onCreateMethod;
    }

}
