package coffee;

import dagger.Injector;
import dagger.ProvidesComponent;
import dagger.ProvidesModule;


@Injector
public class DependencyInjector {

    @ProvidesComponent
    public CoffeeApp.Coffee coffee() {
        return DaggerCoffeeApp_Coffee.builder()
                .dripCoffeeModule(new DripCoffeeModule())
                .build();
    }

    @ProvidesModule
    public AModule aModule() {
        return new AModule();
    }

    @ProvidesModule
    public BModule bModule() {
        return new BModule();
    }

    @ProvidesModule
    public DripCoffeeModule dripCoffeeModule() {
        return new DripCoffeeModule();
    }
}
