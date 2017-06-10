package coffee;

import dagger.*;

import javax.inject.Singleton;

public class CoffeeApp {
  @Singleton
  @Component(modules = { DripCoffeeModule.class })
  public interface Coffee {
    CoffeeMaker maker();
    Thermosiphon thermosiphon();
    @Component.Builder
    interface Builder {
      @BindsInstance Builder app(App app);
      Builder dripCoffeeModule(DripCoffeeModule module);
      Coffee build();
    }

  }

  @ActivityScope
  @Subcomponent(modules = {AModule.class, BModule.class})
  public interface Bleu {
    Integer integer();
    SomeCoffee someCoffee();
    @Subcomponent.Builder
    interface Builder {
      Builder moduleA(AModule module);
      Builder moduleB(BModule module);
      Bleu build();
    }
  }

  @Subcomponent(modules = {BModule.class})
  public interface Bleu2 {
    CoffeeMaker dou();
    @Subcomponent.Builder
    interface Builder {
      Bleu2.Builder someModule(BModule whaaat);
      Bleu2 build();
    }
  }

  public static void main(String[] args) {
    Coffee coffee = DaggerCoffeeApp_Coffee.builder().build();
    coffee.maker().brew();
  }
}
