package coffee;

import dagger.Component;
import dagger.ProvidesModule;
import dagger.Subcomponent;

import javax.inject.Named;
import javax.inject.Singleton;

public class CoffeeApp {
  @Singleton
  @Component(modules = { DripCoffeeModule.class })
  public interface Coffee {
    CoffeeMaker maker();
    Thermosiphon thermosiphon();
    Bleu.Builder any();
    Bleu2.Builder bleu2();
  }

  @ActivityScope
  @Subcomponent(modules = {AModule.class})
  public interface Bleu {
    Integer integer();
    @Subcomponent.Builder
    interface Builder {
      Builder requestModule(AModule module);
      Bleu build();
    }
  }

  @Subcomponent(modules = {BModule.class})
  public interface Bleu2 {
    CoffeeMaker dou();
    @Subcomponent.Builder
    interface Builder {
      Builder requestModule(BModule module);
      Bleu2 build();
    }
  }

  public static void main(String[] args) {
    Coffee coffee = DaggerCoffeeApp_Coffee.builder().build();
    coffee.maker().brew();
  }
}
