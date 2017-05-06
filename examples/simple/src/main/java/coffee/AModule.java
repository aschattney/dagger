package coffee;

import dagger.Module;
import dagger.Provides;

/**
 * Created by Andy on 06.05.2017.
 */
@Module
public class AModule {

    @Provides
    public Integer integer() {
        return 1;
    }

}
