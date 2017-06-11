package dagger.decoder;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

@AutoService(javax.annotation.processing.Processor.class)
public class DecodingProcessor extends AbstractProcessor
{

    public static final String DAGGER_TEST_TRIGGER = "dagger.TestTrigger";
    private Elements elements;
    private Filer filer;
    private boolean alreadyProcessed = false;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment)
    {
        super.init(processingEnvironment);
        elements = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment)
    {
        return !alreadyProcessed && (Utils.isUnitTest(elements) || Utils.isAndroidTest(elements)) && processElement();
    }

    private boolean processElement()
    {
        TypeElement triggerElement = getTriggerElement();
        if (triggerElement == null) {
            return false;
        }
        alreadyProcessed = true;
        DecodingProcessingStep step = new DecodingProcessingStep(filer);
        step.process(triggerElement);
        return false;
    }

    private TypeElement getTriggerElement() {
        return elements.getTypeElement(DAGGER_TEST_TRIGGER);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes()
    {
        return ImmutableSet.of("*");
    }

}
