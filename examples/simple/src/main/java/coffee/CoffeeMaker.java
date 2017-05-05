package coffee;

import dagger.Lazy;
import javax.inject.Inject;

class CoffeeMaker {

  private final Pump pump;
  @Inject
  Heater heater;

  @Inject public CoffeeMaker(Pump pump) {
    this.pump = pump;
  }

  public void brew() {
    heater.on();
    pump.pump();
    System.out.println(" [_]P coffee! [_]P ");
    heater.off();
  }
}
