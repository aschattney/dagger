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
import factories.DaggerComponents_ApplicationComponent;
import injector.Injector;
import injector.InjectorSpec;

import javax.inject.Inject;
import javax.inject.Named;

public class DemoApplication1 extends Application implements InjectorSpec{

  @Inject LocationManager locationManager; // for some reason.
  @Inject @Named("apiKey") String someString;
  @Inject @Named("apiKey1") String anotherString;
  private Components.ApplicationComponent component;
 // private Injector injector = new Injector(this);

  @Override public void onCreate() {
    super.onCreate();
    //component = injector.applicationComponent(new AndroidModule(this), new SingletonModule());
    //component.inject(this);
  }

  public Components.ApplicationComponent component() {
    return component;
  }

  @Override
  public Components.ApplicationComponent.Builder applicationComponent(Components.ApplicationComponent.Builder builder, AndroidModule androidModule) {
    return builder.androidModule(androidModule);
  }

  @Override
  public Injector getInjector() {
    return null;
  }
}
