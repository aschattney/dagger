package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import dagger.Config;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.util.Elements;

public class AppConfig {

    private final TypeElement appClass;
    private final TypeElement baseAppClass;

    public AppConfig(TypeElement appClass, TypeElement baseAppClass) {
        this.appClass = appClass;
        this.baseAppClass = baseAppClass;
    }

    public TypeElement getAppClass() {
        return appClass;
    }

    public TypeElement getBaseAppClass() {
        return baseAppClass;
    }

    static class Factory {

        private Elements elements;

        Factory(Elements elements) {
            this.elements = elements;
        }

        public AppConfig create(Config config) {
            final TypeElement appClass = extractAppClassElement(config);
            final TypeElement baseAppClass = extractBaseAppClassElement(config);
            return new AppConfig(appClass, baseAppClass);
        }

        private TypeElement extractAppClassElement(Config config) {
            TypeElement element;
            try {
                element = elements.getTypeElement(config.applicationClass().getName());
            }catch(MirroredTypeException e) {
                element = MoreTypes.asTypeElement(e.getTypeMirror());
            }
            return element;
        }

        private TypeElement extractBaseAppClassElement(Config config) {
            TypeElement element = elements.getTypeElement(config.baseApplicationClass());
            if (element == null) {
                throw new IllegalArgumentException(
                        String.format("member baseApplicationClass with value \"%s\" in Config Annotation does not resolve to a valid class!", config.baseApplicationClass())
                );
            }
            return element;
        }
    }

}
