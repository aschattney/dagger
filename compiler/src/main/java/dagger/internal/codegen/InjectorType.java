package dagger.internal.codegen;

import javax.lang.model.element.TypeElement;

/**
 * Created by Andy on 05.05.2017.
 */
public class InjectorType {

    private TypeElement element;
    private BindingGraph bindingGraph;

    public InjectorType(TypeElement element, BindingGraph bindingGraph) {
        this.element = element;
        this.bindingGraph = bindingGraph;
    }

    public BindingGraph getBindingGraph() {
        return bindingGraph;
    }

    public TypeElement getElement() {
        return element;
    }
}
