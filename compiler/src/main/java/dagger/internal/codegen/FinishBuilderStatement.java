package dagger.internal.codegen;

import com.google.common.base.Optional;
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
            final Optional<ComponentDescriptor.BuilderSpec> builderSpec = this.descriptor.builderSpec();
            return (builderSpec.isPresent()) ? builder.add(".$L())\n", builderSpec.get().buildMethod().getSimpleName().toString()).build() : builder.build();
        }
        return CodeBlock.of(".build())\n");
    }
}
