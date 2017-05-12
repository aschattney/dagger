package coffee;

import dagger.Binds;
import dagger.Module;

@Module
public abstract class PumpModule {
  @Binds
  abstract Pump providePump(Thermosiphon pump);
}
