package coffee;

import dagger.Module;
import dagger.Provides;

/**
 * Created by Andy on 06.05.2017.
 */
@Module
public class BModule {

    @Provides
    public Double aDouble() {
        return 1.0;
    }

}
