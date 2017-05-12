package dagger.internal.codegen;

import com.squareup.javapoet.ClassName;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DI {
    private TypeElement appClass;
    private Set<TypeElement> components;
    private List<InjectorType> injectorTypes;

    public DI(TypeElement appClass, Set<TypeElement> components, List<InjectorType> injectorTypes) {
        this.appClass = appClass;
        this.components = components;
        this.injectorTypes = injectorTypes;
    }

    public ClassName getClassName() {
        final ClassName className = ClassName.bestGuess(appClass.asType().toString());
        return className.topLevelClassName().peerClass("Test" + className.simpleName());
    }

    public List<InjectorType> getInjectorTypes() {
        return injectorTypes;
    }

    public Set<TypeElement> getComponents() {
        return components;
    }

    public TypeElement getAppClass() {
        return appClass;
    }
}
