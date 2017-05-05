package coffee;

import dagger.Component;

import javax.inject.Named;
import javax.inject.Singleton;

public class CoffeeApp {
  @Singleton
  @Component(modules = { DripCoffeeModule.class })
  public interface Coffee {
    CoffeeMaker maker();
    Thermosiphon thermosiphon();
  }

  @ActivityScope
  @Component(dependencies = {Coffee.class})
  public interface Bleu {
    Thermosiphon thermosiphon();
  }

  public static void main(String[] args) {
    Coffee coffee = DaggerCoffeeApp_Coffee.builder().build();
    coffee.maker().brew();
  }
}
