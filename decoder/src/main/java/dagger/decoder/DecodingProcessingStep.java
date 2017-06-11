package dagger.decoder;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import javax.xml.bind.DatatypeConverter;

import dagger.Trigger;

class DecodingProcessingStep {

    private Filer filer;

    DecodingProcessingStep(Filer filer) {
        this.filer = filer;
    }

    private static byte[] decodeClass(String value) {
        return DatatypeConverter.parseBase64Binary(value);
    }

    public void process(TypeElement typeElement) {
        List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
        enclosedElements.stream()
                        .filter(this::isMethod)
                        .filter(this::hasTriggerAnnotation)
                        .map(element -> (ExecutableElement) element)
                        .map(method -> method.getAnnotation(Trigger.class))
                        .forEach(trigger ->
                        {
                            try {
                                process(trigger);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
    }

    private boolean hasTriggerAnnotation(Element method) {
        return method.getAnnotation(Trigger.class) != null;
    }

    private boolean isMethod(Element element) {
        return element.getKind() == ElementKind.METHOD;
    }

    private void process(Trigger annotation) throws IOException {
        final String value = annotation.value();
        final String qualifiedName = annotation.qualifiedName();
        final JavaFileObject sourceFile = filer.createSourceFile(qualifiedName);
        final OutputStream os = sourceFile.openOutputStream();
        os.write(decodeClass(value));
        os.flush();
        os.close();
    }
}
