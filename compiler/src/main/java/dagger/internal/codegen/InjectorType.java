package dagger.internal.codegen;

import javax.lang.model.element.TypeElement;

/**
 * Created by Andy on 05.05.2017.
 */
public class InjectorType {

    private TypeElement element;
    private BindingGraph bindingGraph;
    private ComponentDescriptor componentDescriptor;

    public InjectorType(TypeElement element, BindingGraph bindingGraph, ComponentDescriptor componentDescriptor) {
        this.element = element;
        this.bindingGraph = bindingGraph;
        this.componentDescriptor = componentDescriptor;
    }

    public BindingGraph getBindingGraph() {
        return bindingGraph;
    }

    public TypeElement getElement() {
        return element;
    }

    public ComponentDescriptor getComponentDescriptor() {
        return componentDescriptor;
    }
}
