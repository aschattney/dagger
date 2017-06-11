package com.example.dagger.simple;

import dagger.BindsInstance;
import dagger.Component;
import javax.inject.Singleton;


public interface Components {

    @Singleton
    @Component(modules = {AndroidModule.class, SingletonModule.class})
    public interface ApplicationComponent {
        void inject(DemoApplication1 app);

        @Component.Builder
        interface Builder {
            @BindsInstance Builder app(DemoApplication1 app);
            ApplicationComponent build();
        }
    }


}
