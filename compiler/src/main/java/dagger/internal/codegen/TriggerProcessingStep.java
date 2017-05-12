package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.Trigger;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by Andy on 07.05.2017.
 */
public class TriggerProcessingStep implements BasicAnnotationProcessor.ProcessingStep {

    private TestRegistry testRegistry;
    private Filer filer;

    public TriggerProcessingStep(TestRegistry testRegistry, Filer filer) {
        this.testRegistry = testRegistry;
        this.filer = filer;
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotations() {
        return ImmutableSet.of(Trigger.class);
    }

    @Override
    public Set<Element> process(SetMultimap<Class<? extends Annotation>, Element> setMultimap) {
        final Set<Element> elements = setMultimap.get(Trigger.class);
        final List<ExecutableElement> executableElements = elements.stream()
                .map(element -> (ExecutableElement) element)
                .collect(Collectors.toList());

        for (ExecutableElement executableElement : executableElements) {
            final Trigger annotation = executableElement.getAnnotation(Trigger.class);
            final String value = annotation.value();
            try {
                byte[] decodedClass = testRegistry.decodeClass(value);
                final JavaFileObject sourceFile = filer.createSourceFile(annotation.qualifiedName());
                final OutputStream os = sourceFile.openOutputStream();
                os.write(decodedClass);
                os.flush();
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ImmutableSet.of();
    }
}
