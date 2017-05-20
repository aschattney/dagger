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
    public void process(TypeSpec.Builder builder) {
        super.process(builder);
        final MethodSpec.Builder methodBuilder = buildMethod();
        methodBuilder.addModifiers(Modifier.ABSTRACT);
        builder.addMethod(methodBuilder.build());
    }

    private MethodSpec.Builder buildMethod() {
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(simpleVariableName(component))
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
            methodSpecs.addAll(((SpecComponentInfo)info).getMethods());
        }
        methodSpecs.add(buildMethod());
        return methodSpecs;
    }
}
