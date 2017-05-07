package coffee;

import dagger.Module;
import dagger.Provides;

/**
 * Created by Andy on 06.05.2017.
 */
@Module
public class BModule {

    private final double A;

    public BModule (double A) {
        this.A = A;
    }

    @Provides
    public Double aDouble() {
        return A;
    }

}
