package dagger.internal.codegen;

import dagger.Component;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.List;

/**
 * Created by Andy on 06.05.2017.
 */
public class ProvidingMethodOverrider {

    private final TypeElement component;
    private final ComponentDescriptor descriptor;
    private final ExecutableElement executableElement;
    private List<InitializationStatement> statements;

    public ProvidingMethodOverrider(TypeElement component, ComponentDescriptor descriptor, ExecutableElement executableElement, List<InitializationStatement> statements) {
        this.component = component;
        this.descriptor = descriptor;
        this.executableElement = executableElement;
        this.statements = statements;
    }

    public TypeElement getComponent() {
        return component;
    }

    public ComponentDescriptor getDescriptor() {
        return descriptor;
    }

    public ExecutableElement getExecutableElement() {
        return executableElement;
    }

    public List<InitializationStatement> getStatements() {
        return statements;
    }
}
