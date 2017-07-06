package coffee;

import dagger.Module;
import dagger.Provides;

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
