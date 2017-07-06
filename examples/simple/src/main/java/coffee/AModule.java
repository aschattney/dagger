package coffee;

import dagger.Module;
import dagger.Provides;


@Module(subcomponents = CoffeeApp.Bleu2.class)
public class AModule {

    @Provides
    public Integer integer() {
        return 1;
    }

}
