package dagger.di;

public interface BaseComponentBuilder<C extends BaseComponent> {
    C build();
}
