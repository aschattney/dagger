package dagger.android.testcase;

import android.app.Application;
import android.support.test.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;

public abstract class EspressoTestCase<T extends Application>
{

    private T app;

    public T app()
    {
        if (this.app == null) {
            final DaggerRunner abstractRunner = (DaggerRunner) InstrumentationRegistry.getInstrumentation();
            this.app = (T) abstractRunner.getApplication();
        }
        return this.app;
    }

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

}
