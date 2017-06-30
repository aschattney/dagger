package dagger.internal.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.Filer;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.util.Optional;
import static dagger.internal.codegen.SourceFiles.generatedTestClassNameForBinding;

public class TestFactoryGenerator extends FactoryGenerator {
    private final AppConfig.Provider appConfigProvider;
    private final TestRegistry testRegistry;

    TestFactoryGenerator(Filer filer, Elements elements, CompilerOptions compilerOptions, InjectValidator injectValidator, AppConfig.Provider appConfigProvider, TestRegistry testRegistry) {
        super(filer, elements, compilerOptions, injectValidator);
        this.appConfigProvider = appConfigProvider;
        this.testRegistry = testRegistry;
    }

    @Override
    protected boolean shouldCheckForDelegate(ContributionBinding binding) {
        return Util.generateTestDelegate(binding);
    }

    @Override
    protected TypeName parameterizedGeneratedTypeNameForBinding(ProvisionBinding binding) {
        return SourceFiles.parameterizedGeneratedTestTypeNameForBinding(binding);
    }

    @Override
    ClassName nameGeneratedType(ProvisionBinding binding) {
        return generatedTestClassNameForBinding(binding);
    }

    @Override
    void generate(ProvisionBinding input) throws SourceFileGenerationException {
        final AppConfig appConfig = appConfigProvider.get();
        if (!appConfig.debug() && appConfig.generateExtendedComponents()) {
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
        }else if(appConfig.generateExtendedComponents()){
            super.generate(input);
        }
    }

}
