package dagger.internal.codegen;

import com.squareup.javapoet.CodeBlock;

/**
 * Created by Andy on 06.05.2017.
 */
public class FinishBuilderStatement implements InitializationStatement{

    private ComponentDescriptor descriptor;

    public FinishBuilderStatement(ComponentDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public CodeBlock get() {
        final CodeBlock.Builder builder = CodeBlock.builder();
        if (descriptor.kind() == ComponentDescriptor.Kind.SUBCOMPONENT) {
            return (this.descriptor.builderSpec().isPresent()) ? builder.add(".build())\n").build() : builder.build();
        }
        return CodeBlock.of(".build())\n");
    }
}
