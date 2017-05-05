package dagger.internal.codegen;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.List;

/**
 * Created by Andy on 05.05.2017.
 */
public class DI {
    private final TypeElement injector;
    private final List<InjectorType> injectors;

    public DI(TypeElement injector, List<InjectorType> injectors) {
        this.injector = injector;
        this.injectors = injectors;
    }

    public TypeElement getInjector() {
        return injector;
    }

    public List<InjectorType> getInjectors() {
        return ImmutableList.copyOf(injectors);
    }

    public ClassName getClassName() {
        final ClassName className = ClassName.bestGuess(injector.asType().toString());
        return ClassName.bestGuess(className.packageName() + "." + "Test" + className.simpleName());
    }
}
