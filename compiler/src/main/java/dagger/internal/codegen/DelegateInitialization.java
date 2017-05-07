package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;

import javax.lang.model.element.Modifier;

import java.util.HashMap;

import static dagger.internal.codegen.Util.bindingSupportsTestDelegate;
import static dagger.internal.codegen.Util.getDelegateTypeName;

/**
 * Created by Andy on 06.05.2017.
 */
public class DelegateInitialization implements InitializationStatement {

    private ComponentDescriptor descriptor;
    private final BindingGraph graph;

    public DelegateInitialization(ComponentDescriptor descriptor, BindingGraph graph) {
        this.descriptor = descriptor;
        this.graph = graph;
    }

    @Override
    public CodeBlock get() {
        final CodeBlock.Builder codeBuilder = CodeBlock.builder();
        final ImmutableCollection<ResolvedBindings> values = graph.resolvedBindings().values();
        for (ResolvedBindings resolvedBindings : values) {
            try {

                if (resolvedBindings.ownedBindings().isEmpty()) {
                    continue;
                }

                ContributionBinding binding = resolvedBindings.contributionBinding();
                if (bindingSupportsTestDelegate(binding) && shouldCreateDelegate(binding)) {
                    final String delegateFieldName = Util.getDelegateFieldName(resolvedBindings.key());
                    final ClassName delegateType = getDelegateTypeName(resolvedBindings.key());
                    final String methodName = "with" + delegateType.simpleName();
                    codeBuilder.add(".$L($L)\n", methodName, delegateFieldName);
                }
            } catch (Exception e) {
            }
        }
        return codeBuilder.build();
    }

    private boolean shouldCreateDelegate(ContributionBinding binding) {
        return descriptor.kind() != ComponentDescriptor.Kind.SUBCOMPONENT || binding.requiresModuleInstance();
    }
}
