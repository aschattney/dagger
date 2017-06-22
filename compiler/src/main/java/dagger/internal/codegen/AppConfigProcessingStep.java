package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.Config;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import java.lang.annotation.Annotation;
import java.util.Set;

public class AppConfigProcessingStep implements BasicProcessor.ProcessingStep {

    private final Messager messager;
    private AppConfig.Validator validator;
    private AppConfig.Factory factory;
    private AppConfig.Provider appConfigProvider;

    public AppConfigProcessingStep(Messager messager,
                                   AppConfig.Validator validator,
                                   AppConfig.Factory factory,
                                   AppConfig.Provider appConfigProvider) {
        this.messager = messager;
        this.validator = validator;
        this.factory = factory;
        this.appConfigProvider = appConfigProvider;
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotations() {
        return ImmutableSet.of(Config.class);
    }

    @Override
    public Set<Element> process(SetMultimap<Class<? extends Annotation>, Element> set, boolean anyElementsRejected) {

        if (appConfigProvider.isSet()) {
            return ImmutableSet.of();
        }

        final Set<Element> elements = set.get(Config.class);
        final ValidationReport<Element> report = validator.validate(elements);
        if (!report.isClean()) {
            report.printMessagesTo(messager);
        }else {
            if (!elements.isEmpty()) {
                final Element element = elements.iterator().next();
                appConfigProvider.set(factory.create(element.getAnnotation(Config.class)));
            }
        }

        return ImmutableSet.of();
    }
}
