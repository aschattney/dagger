package com.example.dagger.simple;

import com.example.dagger.simple.ui.HomeActivity;
import dagger.Component;
import javax.inject.Singleton;


public interface Components {

    @Singleton
    @Component(modules = {AndroidModule.class, SingletonModule.class})
    public interface ApplicationComponent {
        //void inject(DemoApplication1 application);
        void inject(HomeActivity homeActivity);
        void inject(DemoActivity demoActivity);
        void inject(DemoApplication1 app);

        @Component.Builder
        interface Builder {
            Builder androidModule(AndroidModule module);
            Builder singletonModule(SingletonModule module);
            ApplicationComponent build();
        }
    }


}
