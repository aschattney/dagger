package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.Config;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Created by Andy on 26.05.2017.
 */
public class AppConfigProcessingStep implements BasicAnnotationProcessor.ProcessingStep{

    private final Messager messager;
    private final Elements elements;
    private final Types types;
    private AppConfig.Validator validator;

    public AppConfigProcessingStep(Messager messager, Elements elements, Types types, AppConfig.Validator validator) {
        this.messager = messager;
        this.elements = elements;
        this.types = types;
        this.validator = validator;
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotations() {
        return ImmutableSet.of(Config.class);
    }

    @Override
    public Set<Element> process(SetMultimap<Class<? extends Annotation>, Element> set) {
        final Set<Element> elements = set.get(Config.class);
        validator.validate(elements).printMessagesTo(messager);
        return ImmutableSet.of();
    }
}
