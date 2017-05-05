package dagger.internal.codegen;

/**
 * Created by Andy on 05.05.2017.
 */
public class InjectorType {

    private BindingGraph bindingGraph;

    public InjectorType(BindingGraph bindingGraph) {
        this.bindingGraph = bindingGraph;
    }

    public BindingGraph getBindingGraph() {
        return bindingGraph;
    }
}
