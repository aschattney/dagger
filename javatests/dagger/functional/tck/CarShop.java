/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.tck;

import dagger.Component;
import javax.inject.Singleton;
import org.atinject.tck.auto.Car;

@Singleton
@Component(
  modules = {
    CarModule.class,
    TireModule.class,
    SeatModule.class,
    EngineModule.class,
    FuelTankModule.class
  }
)
public interface CarShop {
  @SuppressWarnings("dependency-cycle")
  Car make();
  @Component.Builder
  interface Builder {
    CarShop build();
  }
}
