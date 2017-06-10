package dagger;

public interface BaseComponentBuilder<C extends BaseComponent> {
    C build();
}
