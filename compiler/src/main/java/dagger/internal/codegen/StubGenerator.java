package dagger.internal.codegen;

import java.io.IOException;
import java.util.Optional;
import com.squareup.javapoet.*;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
import static dagger.internal.codegen.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.TypeNames.*;


public class StubGenerator extends SourceFileGenerator<ProvisionBinding> {

    private final List<String> generated = new ArrayList<>();
    private final AppConfig.Provider appConfigProvider;
    private final TestRegistry testRegistry;

    StubGenerator(Filer filer, Elements elements, Types types, AppConfig.Provider appConfigProvider, TestRegistry testRegistry) {
        super(filer, elements);
        this.appConfigProvider = appConfigProvider;
        this.testRegistry = testRegistry;
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
        final String o = generatedTypeName.packageName() + "." + generatedTypeName.simpleName();
        if (generated.contains(o) || !input.shouldGenerateDelegate()) {
            return Optional.empty();
        }
        generated.add(o);
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("get");
        methodBuilder.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
        final TypeName contributedTypeName = ClassName.get(input.contributedType());
        methodBuilder.returns(contributedTypeName);
        HashMap<BindingKey, FrameworkField> fields = new HashMap<>();
        for (Map.Entry<BindingKey, FrameworkField> entry : generateBindingFieldsForDependencies(input).entrySet()) {
            FrameworkField bindingField = entry.getValue();
            fields.put(entry.getKey(), bindingField);
        }

        List<ParameterSpec> parameterSpecList = this.buildMethodParamsList(input, fields);

        return Optional.of(TypeSpec.interfaceBuilder(generatedTypeName)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(methodBuilder.addParameters(parameterSpecList).build()));
    }

    protected List<ParameterSpec> buildMethodParamsList(ProvisionBinding input, HashMap<BindingKey, FrameworkField> fields) {
        List<ParameterSpec> parameterSpecList = new ArrayList<>();
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
        return parameterSpecList;
    }

    @Override
    void generate(ProvisionBinding input) throws SourceFileGenerationException {
        final AppConfig appConfig = appConfigProvider.get();
        if (!appConfig.debug()) {
            ClassName generatedTypeName = nameGeneratedType(input);
            Optional<TypeSpec.Builder> type = write(generatedTypeName, input);
            if (!type.isPresent()) {
                return;
            }
            final ClassName className = nameGeneratedType(input);
            try {
                testRegistry.addEncodedClass(className, buildJavaFile(className, type.get()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else {
            super.generate(input);
        }
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
