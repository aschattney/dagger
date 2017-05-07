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
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;

import javax.inject.Named;
import java.util.Arrays;
import java.util.List;

/**
 * A module to wrap the Activity state and expose it to the graph.
 */
@Module
public class ActivityModule {
  private final Activity activity;

  public ActivityModule(Activity activity) {
    this.activity = activity;
  }

  /**
   * Expose the activity to dependents in the graph.
   */
  @Provides @PerActivity Activity activity() {
    return activity;
  }

  @Provides
  @StringKey("AAA")
  @IntoMap
  public String aString() {
    return "A";
  }

  @Provides
  @StringKey("BBB")
  @IntoMap
  public String bString() {
    return "B";
  }

  @Provides
  @Named("AnyString")
  public String cString() {
    return "B";
  }

  @Provides
  public List<String> list() {
    return Arrays.asList("A", "B", "C");
  }

}
