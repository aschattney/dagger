package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
import java.util.*;

import static dagger.internal.codegen.SourceFiles.generateBindingFieldsForDependencies;


public class StubGenerator extends SourceFileGenerator<ProvisionBinding> {

    private final Types types;

    StubGenerator(Filer filer, Elements elements, Types types) {
        super(filer, elements);
        this.types = types;
    }

    @Override
    ClassName nameGeneratedType(ProvisionBinding input) {
        return Util.getDelegateTypeName(input.key());
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(ProvisionBinding input) {
        return input.bindingElement();
    }

    @Override
    Optional<TypeSpec.Builder> write(ClassName generatedTypeName, ProvisionBinding input) {
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("get");
        methodBuilder.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
        methodBuilder.returns(ClassName.get(input.contributedType()));
        List<ParameterSpec> parameterSpecList = new ArrayList<>();
        HashMap<BindingKey, FrameworkField> fields = new HashMap<>();
        for (Map.Entry<BindingKey, FrameworkField> entry : generateBindingFieldsForDependencies(input).entrySet()) {
            FrameworkField bindingField = entry.getValue();
            fields.put(entry.getKey(), bindingField);
        }

        for (DependencyRequest request : input.explicitDependencies()) {
            final FrameworkField frameworkField = fields.get(request.bindingKey());
            TypeName typeName = request.kind() == DependencyRequest.Kind.INSTANCE ? frameworkField.type().typeArguments.get(0) : frameworkField.type();
            parameterSpecList.add(ParameterSpec.builder(typeName, frameworkField.name()).build());
        }

        return Optional.of(TypeSpec.interfaceBuilder(generatedTypeName)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(methodBuilder.addParameters(parameterSpecList).build()));
    }
}
