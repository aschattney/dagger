package dagger.internal.codegen;

import com.squareup.javapoet.CodeBlock;

public class BuilderStatement implements InitializationStatement {

    private ComponentDescriptor componentDescriptor;

    public BuilderStatement(ComponentDescriptor componentDescriptor) {
        this.componentDescriptor = componentDescriptor;
    }

    @Override
    public CodeBlock get() {
        return CodeBlock.of("$T.builder()", Util.getDaggerComponentClassName(componentDescriptor.componentDefinitionType()));
    }



}
