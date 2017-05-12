package coffee;

import dagger.Injector;
import dagger.ProvidesComponent;
import dagger.ProvidesModule;
import dagger.ProvidesSubcomponent;
import factories.DaggerCoffeeApp_Coffee;
import injector.InjectorSpec;

public class App implements InjectorSpec {

    @Override
    public CoffeeApp.Bleu2.Builder bleu2(CoffeeApp.Bleu2.Builder builder, BModule bModule) {
        return builder.someModule(bModule);
    }

    @Override
    public CoffeeApp.Bleu.Builder bleu(CoffeeApp.Bleu.Builder builder, AModule aModule, BModule bModule) {
        return builder.moduleA(aModule).moduleB(bModule);
    }

    @Override
    public DaggerCoffeeApp_Coffee.Builder coffee(DaggerCoffeeApp_Coffee.Builder builder, DripCoffeeModule dripCoffeeModule) {
        return builder.dripCoffeeModule(dripCoffeeModule);
    }

    @Override
    public injector.Injector getInjector() {
        return new injector.Injector(this);
    }


}
