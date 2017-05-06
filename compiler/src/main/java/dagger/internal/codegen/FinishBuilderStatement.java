package dagger.internal.codegen;

import com.squareup.javapoet.CodeBlock;

/**
 * Created by Andy on 06.05.2017.
 */
public class FinishBuilderStatement implements InitializationStatement{

    @Override
    public CodeBlock get() {
        return CodeBlock.of(".build()");
    }
}
