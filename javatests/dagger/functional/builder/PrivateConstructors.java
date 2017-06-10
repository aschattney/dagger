/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.functional.builder;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;

import javax.inject.Named;
import java.util.Arrays;
import java.util.List;

final class PrivateConstructors {
  @Module
  static final class M {
    @Provides
    static String provideString() {
      return "str";
    }

    private M() {}
  }

  @Component(modules = M.class)
  interface C {
    String string();

    @Component.Builder
    interface Builder {
      // M should not be required, even though the constructor is inaccessible
      C build();
    }
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
    return "C";
  }

  @Provides
  public String dString() {
    return "D";
  }

  @Provides
  public List<String> list() {
    return Arrays.asList("A", "B", "C");
  }

}
