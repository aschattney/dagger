package coffee;

import dagger.Injector;
import dagger.ProvidesComponent;


@Injector
public class DependencyInjector {

    @ProvidesComponent
    public CoffeeApp.Coffee coffee() {
        return DaggerCoffeeApp_Coffee.builder()
                .dripCoffeeModule(new DripCoffeeModule())
                .build();
    }

}
