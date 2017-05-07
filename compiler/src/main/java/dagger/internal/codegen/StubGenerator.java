package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.*;
import dagger.Lazy;

import javax.annotation.processing.Filer;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
import java.util.*;

import static dagger.internal.codegen.SourceFiles.DEPENDENCY_ORDERING;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.TypeNames.*;


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
        final TypeName contributedTypeName = ClassName.get(input.contributedType());
        methodBuilder.returns(contributedTypeName);
        List<ParameterSpec> parameterSpecList = new ArrayList<>();
        HashMap<BindingKey, FrameworkField> fields = new HashMap<>();
        for (Map.Entry<BindingKey, FrameworkField> entry : generateBindingFieldsForDependencies(input).entrySet()) {
            FrameworkField bindingField = entry.getValue();
            fields.put(entry.getKey(), bindingField);
        }

        for (DependencyRequest request : input.explicitDependencies()) {
            final FrameworkField frameworkField = fields.get(request.bindingKey());
            TypeName typeName = request.kind() == DependencyRequest.Kind.INSTANCE ?
                    frameworkField.type().typeArguments.get(0) :
                    frameworkTypeUsageStatement(frameworkField.type().typeArguments.get(0), request.kind());

            final String name = request.kind() == DependencyRequest.Kind.INSTANCE ?
                    frameworkField.name().replaceAll("Provider$", "") :
                    frameworkField.name();
            parameterSpecList.add(ParameterSpec.builder(typeName, name).build());
        }

        return Optional.of(TypeSpec.interfaceBuilder(generatedTypeName)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(methodBuilder.addParameters(parameterSpecList).build()));
    }

    static TypeName frameworkTypeUsageStatement(TypeName type, DependencyRequest.Kind dependencyKind) {
        switch (dependencyKind) {
            case LAZY:
                return ParameterizedTypeName.get(LAZY, type);
            case INSTANCE:
                return type;
            case PROVIDER:
                return ParameterizedTypeName.get(PROVIDER, type);
            case PRODUCER:
                return ParameterizedTypeName.get(PRODUCER, type);
            case MEMBERS_INJECTOR:
                return ParameterizedTypeName.get(MEMBERS_INJECTOR, type);
            case PROVIDER_OF_LAZY:
                return ParameterizedTypeName.get(PROVIDER_OF_LAZY, type);
            default:
                return ParameterizedTypeName.get(PROVIDER, type);
        }
    }

}
