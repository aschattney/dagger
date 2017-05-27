package coffee;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module(includes = PumpModule.class, subcomponents = {CoffeeApp.Bleu.class})
public class DripCoffeeModule {
  @Provides @Singleton Heater provideHeater() {
    return new ElectricHeater();
  }
  @Provides @Singleton String provideString() {
    return "";
  }
}
