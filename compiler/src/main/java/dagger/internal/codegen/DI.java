package dagger.internal.codegen;

import com.squareup.javapoet.ClassName;
import dagger.Config;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DI {
    private Set<TypeElement> components;
    private List<InjectorType> injectorTypes;
    private AppConfig config;
    private ClassName decoratorType;

    public DI(AppConfig config, Set<TypeElement> components, List<InjectorType> injectorTypes, ClassName decoratorType) {
        this.config = config;
        this.components = components;
        this.injectorTypes = injectorTypes;
        this.decoratorType = decoratorType;
    }

    public ClassName getClassName() {
        final ClassName className = ClassName.bestGuess(this.getAppClass().asType().toString());
        return className.topLevelClassName().peerClass("Test" + className.simpleName());
    }

    public List<InjectorType> getInjectorTypes() {
        return injectorTypes;
    }

    public Set<TypeElement> getComponents() {
        return components;
    }

    public TypeElement getAppClass() {
        return config.getAppClass();
    }

    public TypeElement getBaseAppClass() { return config.getBaseAppClass(); }

    public ClassName getDecoratorType() { return this.decoratorType; }
}
