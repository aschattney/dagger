package coffee;

import javax.inject.Inject;
import javax.inject.Provider;

public class SomeCoffee {

    private Provider<CoffeeApp.Bleu2.Builder> builderProvider;

    @Inject
    public SomeCoffee(Provider<CoffeeApp.Bleu2.Builder> builderProvider) {
        this.builderProvider = builderProvider;
    }

}
