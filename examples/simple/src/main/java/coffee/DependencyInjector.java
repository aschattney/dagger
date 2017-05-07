package coffee;

import dagger.Injector;
import dagger.ProvidesComponent;
import dagger.ProvidesModule;
import dagger.ProvidesSubcomponent;


@Injector
public class DependencyInjector {

    @ProvidesComponent
    public CoffeeApp.Coffee coffee() {
        return DaggerCoffeeApp_Coffee.builder()
                .dripCoffeeModule(new DripCoffeeModule())
                .build();
    }

    @ProvidesSubcomponent
    public CoffeeApp.Bleu bleu(CoffeeApp.Coffee coffee, double d) {
        return coffee.plus(new AModule(), new BModule(d));
    }

    @ProvidesSubcomponent
    public CoffeeApp.Bleu2 bleu2(CoffeeApp.Bleu bleu, double d) {
        return bleu.someComponent()
                .someModule(new BModule(d))
                .build();
    }


    @ProvidesModule
    public BModule moduleB(double a) {
        return new BModule(a);
    }
}
