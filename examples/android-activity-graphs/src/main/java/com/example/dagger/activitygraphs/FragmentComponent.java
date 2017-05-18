package com.example.dagger.activitygraphs;

import dagger.Component;

@PerFragment
@Component(dependencies = AbstractActivityComponent.class)
public interface FragmentComponent {
    @Component.Builder
    interface Builder {
        Builder component(AbstractActivityComponent component);
        FragmentComponent build();
    }
}
