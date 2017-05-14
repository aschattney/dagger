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

/**
 * Created by Andy on 12.05.2017.
 */
public class SpecComponentInfo extends ComponentInfo {

    protected SpecComponentInfo(TypeElement component, ComponentDescriptor descriptor, BindingGraph bindingGraph) {
        super(component, descriptor, bindingGraph);
    }

    @Override
    public void process(TypeSpec.Builder builder) {
        super.process(builder);

        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(simpleVariableName(component))
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

        ClassName builderClassName = getBuilderClassName(component);
        methodBuilder.returns(builderClassName);
        List<ParameterSpec> parameterSpecs = new ArrayList<>();

        ParameterSpec builderParameter = ParameterSpec.builder(builderClassName, "builder").build();

        parameterSpecs.add(builderParameter);

        if (descriptor.builderSpec().isPresent()) {
            for (ComponentDescriptor.BuilderRequirementMethod requirementMethod : descriptor.builderSpec().get().requirementMethods()) {
                final ComponentRequirement requirement = requirementMethod.requirement();
                final TypeElement typeElement = requirement.typeElement();
                if ((requirement.kind() == ComponentRequirement.Kind.MODULE && hasNotOnlyNoArgConstructor(typeElement, requirement.autoCreate())) || requirement.kind() != ComponentRequirement.Kind.MODULE) {
                    parameterSpecs.add(ParameterSpec.builder(ClassName.get(typeElement), simpleVariableName(typeElement)).build());
                }
            }
        } else {
            for (ModuleDescriptor moduleDescriptor : descriptor.modules()) {
                final TypeElement typeElement = moduleDescriptor.moduleElement();
                if (hasNotOnlyNoArgConstructor(typeElement, autoCreate(typeElement))) {
                    parameterSpecs.add(ParameterSpec.builder(ClassName.get(typeElement), simpleVariableName(typeElement)).build());
                }
            }

            for (TypeElement typeElement : descriptor.dependencies()) {
                parameterSpecs.add(ParameterSpec.builder(ClassName.get(typeElement), simpleVariableName(typeElement)).build());
            }
        }

        builder.addMethod(methodBuilder.addParameters(parameterSpecs).build());
    }
}
