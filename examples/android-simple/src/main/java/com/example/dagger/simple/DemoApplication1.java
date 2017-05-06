/*
 * Copyright (C) 2013 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.dagger.simple;

import android.app.Application;
import android.location.LocationManager;
import com.example.dagger.simple.ui.HomeActivity;
import dagger.Component;
import dagger.Injector;
import dagger.ProvidesComponent;
import dagger.ProvidesModule;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Injector
public class DemoApplication1 extends Application {
  
  @Singleton
  @Component(modules = {AndroidModule.class, SingletonModule.class})
  public interface ApplicationComponent {
    void inject(DemoApplication1 application);
    void inject(HomeActivity homeActivity);
    void inject(DemoActivity demoActivity);
  }
  
  @Inject LocationManager locationManager; // for some reason.
  @Inject @Named("apiKey") String someString;
  @Inject @Named("apiKey1") String anotherString;
  private ApplicationComponent component;

  @Override public void onCreate() {
    super.onCreate();
    component = DaggerDemoApplication1_ApplicationComponent.builder()
        .androidModule(new AndroidModule(this))
        .singletonModule(new SingletonModule())
        .build();
    component().inject(this); // As of now, LocationManager should be injected into this.
  }

  @ProvidesComponent
  public ApplicationComponent component() {
    return component;
  }

}
