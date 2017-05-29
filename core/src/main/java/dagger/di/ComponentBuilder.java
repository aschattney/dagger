package dagger.di;

import java.util.Map;

import javax.inject.Provider;

public class ComponentBuilder<P> {

    private final Map<Class<?>, Provider<P>> builders;

    public ComponentBuilder(Map<Class<?>, Provider<P>> builders) {
        this.builders = builders;
    }

    public <A, C extends BaseComponent<A>, T extends BaseComponentBuilder<C>> C getComponent(Class<T> clazz,
                                                                                             A injectable,
                                                                                             Config<A, C, T> config) {
        T builder = (T) builders.get(injectable.getClass()).get();
        C component = config.configure(builder);
        component.injectMembers(injectable);
        return component;
    }

    public interface Config<A, C extends BaseComponent<A>, T extends BaseComponentBuilder<C>> {
        C configure(T builder);
    }

}
