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
    public CoffeeApp.Bleu bleu(CoffeeApp.Coffee coffee) {
        return coffee.any()
                .requestModule(new AModule())
                .build();
    }

    @ProvidesSubcomponent
    public CoffeeApp.Bleu2 bleu2(CoffeeApp.Coffee bleu) {
        return bleu.bleu2()
                .requestModule(new BModule())
                .build();
    }
}
