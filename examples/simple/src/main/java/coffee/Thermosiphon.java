package coffee;

import javax.inject.Inject;
import javax.inject.Provider;

public class Thermosiphon implements Pump {
  private final Provider<Heater> heater;

  @Inject
  Thermosiphon(Provider<Heater> heater) {
    this.heater = heater;
  }

  @Override public void pump() {
    if (heater.get().isHot()) {
      System.out.println("=> => pumping => =>");
    }
  }
}
