package dagger.internal.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

import static dagger.internal.codegen.AbstractComponentWriter.simpleVariableName;

public class SpecComponentInfo extends ComponentInfo {

    protected SpecComponentInfo(TypeElement component, ComponentDescriptor descriptor, BindingGraph bindingGraph) {
        super(component, descriptor, bindingGraph);
    }

    @Override
    protected String getId() {
        return "decorate" + component.getSimpleName().toString();
    }

    @Override
    public List<String> process(TypeSpec.Builder builder) {
        List<String> ids = super.process(builder);
        if (ids.contains(getId())) {
            return ids;
        }
        final MethodSpec.Builder methodBuilder = buildMethod();
        methodBuilder.addModifiers(Modifier.ABSTRACT);
        MethodSpec method = methodBuilder.build();
        builder.addMethod(method);
        ids.add(getId());
        return ids;
    }

    private MethodSpec.Builder buildMethod() {
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(getId())
                .addModifiers(Modifier.PUBLIC);

        ClassName builderClassName = getBuilderClassName(component);
        methodBuilder.returns(builderClassName);

        List<ParameterSpec> parameterSpecs = new ArrayList<>();
        ParameterSpec builderParameter = ParameterSpec.builder(builderClassName, "builder").build();
        parameterSpecs.add(builderParameter);

        methodBuilder.addParameters(parameterSpecs);
        return methodBuilder;
    }

    public List<MethodSpec.Builder> getMethods() {
        List<MethodSpec.Builder> methodSpecs = new ArrayList<>();
        for (ComponentInfo info : infos) {
            methodSpecs.addAll(((SpecComponentInfo) info).getMethods());
        }
        methodSpecs.add(buildMethod());
        return methodSpecs;
    }
}
