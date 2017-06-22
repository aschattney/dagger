package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.sun.jdi.Mirror;
import dagger.Config;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Set;

public class AppConfig {

    private final TypeElement appClass;
    private final TypeElement baseAppClass;
    private boolean debug;

    public AppConfig(TypeElement appClass, TypeElement baseAppClass, boolean debug) {
        this.appClass = appClass;
        this.baseAppClass = baseAppClass;
        this.debug = debug;
    }

    public TypeElement getAppClass() {
        return appClass;
    }

    public TypeElement getBaseAppClass() {
        return baseAppClass;
    }

    public boolean debug() {
        return this.debug;
    }

    static class Factory {

        private Elements elements;

        Factory(Elements elements) {
            this.elements = elements;
        }

        public AppConfig create(Config config) {
            final TypeElement appClass = extractAppClassElement(config);
            final TypeElement baseAppClass = extractBaseAppClassElement(config);
            boolean debug = config.debug();
            return new AppConfig(appClass, baseAppClass, debug);
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

    static class Provider {
        private AppConfig appConfig;

        public void set(AppConfig appConfig) {
            this.appConfig = appConfig;
        }

        public AppConfig get() {
            return appConfig;
        }

        public boolean isSet() {
            return appConfig != null;
        }
    }

    static class Validator {

        private final Elements elements;
        private Types types;

        public Validator(Elements elements, Types types) {
            this.elements = elements;
            this.types = types;
        }

        public ValidationReport<Element> validate(Set<Element> elements) {
            Element configElement = getConfigType();
            final ValidationReport.Builder<Element> reportBuilder = ValidationReport.about(configElement);
            if (elements.isEmpty()) {
                reportBuilder.addError(String.format("No class is annotated with @%s!", Config.class.getName()));
            }else if(elements.size() > 1) {
                 reportBuilder.addError(String.format("Only one Annotation of type %s is allowed!", Config.class.getName()));
            }else {
                Element element = elements.iterator().next();
                final Config config = element.getAnnotation(Config.class);
                TypeMirror appClass = getApplicationClass(config);
                /*if (!isSubtypeOfApplicationType(appClass)) {
                    reportBuilder.addError(String.format("Class %s is not a subtype of android.app.Application!", appClass.toString()));
                }*/
                TypeMirror baseAppClass = getBaseApplicationClass(config);
                final String baseAppClassName = config.baseApplicationClass();
                if (baseAppClass == null) {
                    reportBuilder.addError(String.format("baseApplicationClass: %s not found!", baseAppClassName));
                }else if (!isSubtypeOfApplicationType(baseAppClass)){
                    reportBuilder.addError(String.format("Class %s is not a subtype of android.app.Application!", baseAppClassName));
                }
            }
            return reportBuilder.build();
        }

        private TypeMirror getBaseApplicationClass(Config config) {
            final TypeElement typeElement = elements.getTypeElement(config.baseApplicationClass());
            return typeElement != null ? typeElement.asType() : null;
        }

        protected boolean isSubtypeOfApplicationType(TypeMirror appClass) {
            return types.isAssignable(appClass, this.elements.getTypeElement("android.app.Application").asType());
        }

        private TypeMirror getApplicationClass(Config config) {
            try{
                return (elements.getTypeElement(config.applicationClass().getName())).asType();
            }catch(MirroredTypeException e) {
                return e.getTypeMirror();
            }
        }

        private Element getConfigType() {
            return elements.getTypeElement(Config.class.getName());
        }
    }

}
