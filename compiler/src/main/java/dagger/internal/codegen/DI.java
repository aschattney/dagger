package dagger.internal.codegen;

import com.squareup.javapoet.ClassName;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.List;

/**
 * Created by Andy on 05.05.2017.
 */
public class DI {
    private final TypeElement injector;
    private List<ExecutableElement> methods;
    private List<InjectorType> injectorTypes;

    public DI(TypeElement injector, List<ExecutableElement> methods, List<InjectorType> injectorTypes) {
        this.injector = injector;
        this.methods = methods;
        this.injectorTypes = injectorTypes;
    }

    public TypeElement getInjector() {
        return injector;
    }

    public ClassName getClassName() {
        final ClassName className = ClassName.bestGuess(injector.asType().toString());
        return ClassName.bestGuess(className.packageName() + "." + "Test" + className.simpleName());
    }

    public List<ExecutableElement> getMethods() {
        return methods;
    }

    public List<InjectorType> getInjectorTypes() {
        return injectorTypes;
    }
}
