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

package com.example.dagger.activitygraphs;

import android.app.Activity;
import android.app.Application;
import android.location.LocationManager;
import com.example.dagger.activitygraphs.ui.HomeComponent;
import dagger.Injector;
import dagger.Provides;
import dagger.ProvidesComponent;
import dagger.ProvidesModule;
import injector.InjectorSpec;

import javax.inject.Inject;

public class DemoApplication extends DaggerApplication {
  private ApplicationComponent applicationComponent;

  // TODO(cgruber): Figure out a better example of something one might inject into the app.
  @Inject LocationManager locationManager; // to illustrate injecting something into the app.

  @Override public void onCreate() {
    super.onCreate();
    applicationComponent = DaggerApplicationComponent.builder()
        .module(new DemoApplicationModule(this))
        .build();
  }

  public ApplicationComponent component() {
    return applicationComponent;
  }

}
