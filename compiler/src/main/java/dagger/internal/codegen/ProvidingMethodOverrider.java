package dagger.internal.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dagger.internal.codegen.Util.createDelegateFieldAndMethod;

/**
 * Created by Andy on 06.05.2017.
 */
public class ProvidingMethodOverrider {

    private final TypeElement component;
    private final ComponentDescriptor descriptor;
    private final ExecutableElement executableElement;
    private List<InitializationStatement> statements;
    private BindingGraph bindingGraph;
    private List<ProvidingMethodOverrider> subcomponentOverriders = new ArrayList<>();

    public ProvidingMethodOverrider(TypeElement component, ComponentDescriptor descriptor, ExecutableElement executableElement, List<InitializationStatement> statements, BindingGraph bindingGraph) {
        this.component = component;
        this.descriptor = descriptor;
        this.executableElement = executableElement;
        this.statements = statements;
        this.bindingGraph = bindingGraph;
    }

    public TypeElement getComponent() {
        return component;
    }

    public ComponentDescriptor getDescriptor() {
        return descriptor;
    }

    public ExecutableElement getExecutableElement() {
        return executableElement;
    }

    public List<InitializationStatement> getStatements() {
        return statements;
    }

    public void add(ProvidingMethodOverrider providingMethodOverrider) {
        subcomponentOverriders.add(providingMethodOverrider);
    }

    public void process(TypeSpec.Builder builder, ClassName generatedTypeName, Map<Key, String> delegateFieldNames) {
        for (ProvidingMethodOverrider subcomponentOverrider : subcomponentOverriders) {
            subcomponentOverrider.process(builder, generatedTypeName, delegateFieldNames);
        }

        this.getBindingGraph().resolvedBindings().values().forEach(resolvedBindings -> {
            if (!resolvedBindings.isEmpty() && resolvedBindings.bindingType() == BindingType.PROVISION) {
                if (!delegateFieldNames.containsKey(resolvedBindings.key())) {
                    createDelegateFieldAndMethod(generatedTypeName, builder, resolvedBindings, delegateFieldNames);
                }
            }
        });

        final MethodSpec.Builder methodSpec = MethodSpec.overriding(this.getExecutableElement());
        final List<InitializationStatement> statements = this.getStatements();
        final CodeBlock.Builder codeBuilder = CodeBlock.builder();
        for (InitializationStatement statement : statements) {
            codeBuilder.add(statement.get());
        }
        methodSpec.addStatement("return $L", codeBuilder.build());
        builder.addMethod(methodSpec.build());
    }

    public BindingGraph getBindingGraph() {
        return bindingGraph;
    }
}
