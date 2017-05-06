package dagger.internal.codegen;

import com.google.common.base.Optional;
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

    private final BindingGraph graph;

    public DelegateInitialization(BindingGraph graph) {
        this.graph = graph;
    }

    @Override
    public CodeBlock get() {
        final CodeBlock.Builder codeBuilder = CodeBlock.builder();
        try {
            HashMap<Key, String> delegateFieldNames = new HashMap<>();
            for (ResolvedBindings resolvedBindings : graph.resolvedBindings().values()) {
                ContributionBinding binding = resolvedBindings.contributionBinding();
                if (bindingSupportsTestDelegate(binding)) {
                    final String delegateFieldName = Util.getDelegateFieldName(resolvedBindings.binding().key());
                    final ClassName delegateType = getDelegateTypeName(resolvedBindings.binding().key());
                    delegateFieldNames.put(resolvedBindings.key(), delegateFieldName);
                    final String methodName = "with" + delegateType.simpleName();
                    codeBuilder.add(".$L($L)", methodName, delegateFieldName);
                }
            }
        }catch(Exception e) {}
        return codeBuilder.build();
    }
}
