package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Decorator  extends SourceFileGenerator<BindingGraph>{

    private BindingGraph.Factory factory;

    private Decorator(Filer filer, Elements elements, BindingGraph.Factory factory) {
        super(filer, elements);
        this.factory = factory;
    }

    @Override
    ClassName nameGeneratedType(BindingGraph input) {
        final TypeElement component = input.componentDescriptor().componentDefinitionType();
        return ClassName.bestGuess("factories." + component.getSimpleName().toString() + "Decorator");
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(BindingGraph input) {
        return Optional.of(input.componentDescriptor().componentDefinitionType());
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, BindingGraph input) {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(generatedTypeName)
                .addModifiers(Modifier.PUBLIC);

        final String daggerBuilderClassName = TriggerComponentInfo.resolveClassName(factory, input.componentDescriptor());

        addDecoratorType(builder, generatedTypeName, daggerBuilderClassName, input);

        return Optional.of(builder);
    }

    private void addDecoratorType(TypeSpec.Builder builder, ClassName returnType, String className, BindingGraph bindingGraph) {
        builder.addModifiers(Modifier.PUBLIC);
        builder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build());
        List<CodeBlock> statements = new ArrayList<>();
        for (ContributionBinding contributionBinding : bindingGraph.delegateRequirements()) {
            Util.createDelegateFieldAndMethod(returnType, builder, contributionBinding, new HashMap<>(1));
            final String delegateFieldName = Util.getDelegateFieldName(contributionBinding.key());
            final ClassName delegateTypeName = Util.getDelegateTypeName(contributionBinding.key());
            statements.add(CodeBlock.of("builder.$L(this.$L);", Util.getDelegateMethodName(delegateTypeName), delegateFieldName));
        }

        builder.addMethod(MethodSpec.methodBuilder("decorate")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.bestGuess(className), "builder")
                .addCode("$L", CodeBlocks.join(statements, "\n"))
                .returns(void.class)
                .build());
    }


    public static class Factory {

        private final Filer filer;
        private final Elements elements;
        private final BindingGraph.Factory bindingGraphFactory;

        Factory(Filer filer, Elements elements, BindingGraph.Factory bindingGraphFactory) {
            this.filer = filer;
            this.elements = elements;
            this.bindingGraphFactory = bindingGraphFactory;
        }

        public Decorator create() {
            return new Decorator(filer, elements, bindingGraphFactory);
        }

    }

}
