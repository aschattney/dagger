/*
 * Copyright (C) 2013 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreElements.hasModifiers;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.collect.Lists.asList;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.Binds;
import dagger.Provides;
import dagger.producers.Produces;
import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collector;
import javax.inject.Named;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.Types;

/**
 * Utilities for handling types in annotation processors
 */
final class Util {
  /**
   * Returns true if the passed {@link TypeElement} requires a passed instance in order to be used
   * within a component.
   */
  static boolean requiresAPassedInstance(Elements elements, Types types, TypeElement typeElement) {
    ImmutableSet<ExecutableElement> methods =
        getLocalAndInheritedMethods(typeElement, types, elements);
    boolean foundInstanceMethod = false;
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(ABSTRACT)
          && !MoreElements.isAnnotationPresent(method, Binds.class)) {
        /* We found an abstract method that isn't a @Binds method.  That automatically means that
         * a user will have to provide an instance because we don't know which subclass to use. */
        return true;
      } else if (!method.getModifiers().contains(STATIC)
          && isAnyAnnotationPresent(method, Provides.class, Produces.class)) {
        foundInstanceMethod = true;
      }
    }

    if (foundInstanceMethod) {
      return !componentCanMakeNewInstances(typeElement);
    }

    return false;
  }

  /**
   * Returns true if and only if a component can instantiate new instances (typically of a module)
   * rather than requiring that they be passed.
   */
  static boolean componentCanMakeNewInstances(TypeElement typeElement) {
    switch (typeElement.getKind()) {
      case CLASS:
        break;
      case ENUM:
      case ANNOTATION_TYPE:
      case INTERFACE:
        return false;
      default:
        throw new AssertionError("TypeElement cannot have kind: " + typeElement.getKind());
    }

    if (typeElement.getModifiers().contains(ABSTRACT)) {
      return false;
    }

    if (requiresEnclosingInstance(typeElement)) {
      return false;
    }

    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind().equals(CONSTRUCTOR)
          && ((ExecutableElement) enclosed).getParameters().isEmpty()
          && !enclosed.getModifiers().contains(PRIVATE)) {
        return true;
      }
    }

    // TODO(gak): still need checks for visibility

    return false;
  }

  private static boolean requiresEnclosingInstance(TypeElement typeElement) {
    switch (typeElement.getNestingKind()) {
      case TOP_LEVEL:
        return false;
      case MEMBER:
        return !typeElement.getModifiers().contains(STATIC);
      case ANONYMOUS:
      case LOCAL:
        return true;
      default:
        throw new AssertionError(
            "TypeElement cannot have nesting kind: " + typeElement.getNestingKind());
    }
  }

  static ImmutableSet<ExecutableElement> getUnimplementedMethods(
      Elements elements, Types types, TypeElement type) {
    return FluentIterable.from(getLocalAndInheritedMethods(type, types, elements))
        .filter(hasModifiers(ABSTRACT))
        .toSet();
  }

  /** A function that returns the input as a {@link DeclaredType}. */
  static final Function<TypeElement, DeclaredType> AS_DECLARED_TYPE =
      typeElement -> asDeclared(typeElement.asType());

  /**
   * A visitor that returns the input or the closest enclosing element that is a
   * {@link TypeElement}.
   */
  static final ElementVisitor<TypeElement, Void> ENCLOSING_TYPE_ELEMENT =
      new SimpleElementVisitor6<TypeElement, Void>() {
        @Override
        protected TypeElement defaultAction(Element e, Void p) {
          return visit(e.getEnclosingElement());
        }

        @Override
        public TypeElement visitType(TypeElement e, Void p) {
          return e;
        }
      };

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose
   * {@linkplain AnnotationMirror#getAnnotationType() annotation type} has the same canonical name
   * as any of that of {@code annotationClasses}.
   */
  // TODO(dpb): Move to MoreElements.
  static boolean isAnyAnnotationPresent(
      Element element, Iterable<? extends Class<? extends Annotation>> annotationClasses) {
    for (Class<? extends Annotation> annotation : annotationClasses) {
      if (MoreElements.isAnnotationPresent(element, annotation)) {
        return true;
      }
    }
    return false;
  }

  @SafeVarargs
  static boolean isAnyAnnotationPresent(
      Element element,
      Class<? extends Annotation> first,
      Class<? extends Annotation>... otherAnnotations) {
    return isAnyAnnotationPresent(element, asList(first, otherAnnotations));
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose {@linkplain
   * AnnotationMirror#getAnnotationType() annotation type} is equivalent to {@code annotationType}.
   */
  // TODO(dpb): Move to MoreElements.
  static boolean isAnnotationPresent(Element element, TypeMirror annotationType) {
    return element
        .getAnnotationMirrors()
        .stream()
        .map(AnnotationMirror::getAnnotationType)
        .anyMatch(candidate -> MoreTypes.equivalence().equivalent(candidate, annotationType));
  }

  /**
   * The elements in {@code elements} that are annotated with an annotation of type
   * {@code annotation}.
   */
  static <E extends Element> FluentIterable<E> elementsWithAnnotation(
      Iterable<E> elements, final Class<? extends Annotation> annotation) {
    return FluentIterable.from(elements)
        .filter(element -> MoreElements.isAnnotationPresent(element, annotation));
  }

  /** A function that returns the simple name of an element. */
  static final Function<Element, String> ELEMENT_SIMPLE_NAME =
      element -> element.getSimpleName().toString();

  /**
   * A {@link Comparator} that puts absent {@link Optional}s before present ones, and compares
   * present {@link Optional}s by their values.
   */
  static <C extends Comparable<C>> Comparator<Optional<C>> optionalComparator() {
    return Comparator.comparing((Optional<C> optional) -> optional.isPresent())
        .thenComparing(Optional::get);
  }

  /**
   * Returns a {@link Collector} that accumulates the input elements into a new {@link
   * ImmutableList}, in encounter order.
   */
  static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
    return collectingAndThen(toList(), ImmutableList::copyOf);
  }

  /**
   * Returns a {@link Collector} that accumulates the input elements into a new {@link
   * ImmutableSet}, in encounter order.
   */
  static <T> Collector<T, ?, ImmutableSet<T>> toImmutableSet() {
    return collectingAndThen(toList(), ImmutableSet::copyOf);
  }

  static ClassName getDelegateTypeName(Key key) {
    final ClassName topLevelClassName = getTopLevelClassName(key.type());
    if (key.qualifier().isPresent()) {
      final AnnotationMirror annotationMirror = key.qualifier().get();
      String value = "";
      for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues().entrySet()) {
        if (entry.getKey().getSimpleName().toString().equals("value")) {
          value = entry.getValue().getValue().toString();
          break;
        }
      }
      return ClassName.bestGuess(topLevelClassName.packageName() + "." + capitalizeFirstLetter(value) + "Delegate");
    }else {
      return ClassName.bestGuess(getBaseDelegateClassName(key.type()) + "Delegate");
    }
  }

  static String getDelegateFieldName(Key key) {

    if (key.qualifier().isPresent()) {
      final AnnotationMirror annotationMirror = key.qualifier().get();
      String value = "";
      for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues().entrySet()) {
        if (entry.getKey().getSimpleName().toString().equals("value")) {
          value = entry.getValue().getValue().toString();
          break;
        }
      }
      return lowerCaseFirstLetter(value) + "Delegate";
    }else {
      return lowerCaseFirstLetter(ClassName.bestGuess(key.type().toString()).simpleName()) + "Delegate";
    }
  }

  static ClassName getDelegateTypeName(ProvisionBinding binding) {
    String baseClassName;
    if (binding.requiresModuleInstance()) {
      ExecutableElement element = (ExecutableElement) binding.bindingElement().get();
      baseClassName = getBaseDelegateClassName(binding.contributedType(), element);
      return ClassName.bestGuess(baseClassName + "Delegate");
    }else {
      baseClassName = getBaseDelegateClassName(binding.key().type());
      return ClassName.bestGuess(baseClassName + "Delegate");
    }
  }

  private static String getBaseDelegateClassName(TypeMirror typeMirror, Element element) {
    String baseClassName;
    ClassName bestGuess = getTopLevelClassName(typeMirror);
    if (element.getAnnotation(Named.class) != null) {
      baseClassName = bestGuess.packageName() + "." + getCapitalizedAnnotationValue(element);
    }else if (typeMirror.getAnnotation(Named.class) != null) {
      baseClassName = bestGuess.packageName() + "." + getCapitalizedAnnotationValue(typeMirror);
    }else {
      baseClassName = typeMirror.toString();
    }
    return baseClassName;
  }

  private static String getCapitalizedAnnotationValue(Element element) {
    return capitalizeFirstLetter(element.getAnnotation(Named.class).value());
  }

  private static ClassName getTopLevelClassName(TypeMirror typeMirror) {
    return ClassName.bestGuess(typeMirror.toString()).topLevelClassName();
  }

  private static String getBaseDelegateClassName(TypeMirror typeMirror) {
    String baseClassName;
    ClassName bestGuess = getTopLevelClassName(typeMirror);
    if (typeMirror.getAnnotation(Named.class) != null) {
      baseClassName = bestGuess.packageName() + "." + getCapitalizedAnnotationValue(typeMirror);
    }else {
      baseClassName = typeMirror.toString();
    }
    return baseClassName;
  }

  private static String getCapitalizedAnnotationValue(TypeMirror typeMirror) {
    return capitalizeFirstLetter(typeMirror.getAnnotation(Named.class).value());
  }

  private static String capitalizeFirstLetter(String original) {
    if (original == null || original.length() == 0) {
      return original;
    }
    return original.substring(0, 1).toUpperCase() + original.substring(1);
  }

  private static String lowerCaseFirstLetter(String original) {
    if (original == null || original.length() == 0) {
      return original;
    }
    return original.substring(0, 1).toLowerCase() + original.substring(1);
  }

  public static boolean supportsTestDelegate(ContributionBinding binding) {
    return binding.factoryCreationStrategy() != ContributionBinding.FactoryCreationStrategy.DELEGATE;
  }

  private Util() {}

  public static void createDelegateFieldAndMethod(ClassName generatedTypeName, TypeSpec.Builder classBuilder, ResolvedBindings resolvedBindings, Map<Key, String> delegateFieldNames) {
      try {
          final FrameworkField contributionBindingField = FrameworkField.forResolvedBindings(resolvedBindings, Optional.absent());
          ContributionBinding binding = resolvedBindings.contributionBinding();
          if (supportsTestDelegate(binding)) {
              final String delegateFieldName = contributionBindingField.name() + "Delegate";
              final ClassName delegateType = getDelegateTypeName(resolvedBindings.binding().key());
              final FieldSpec.Builder builder = FieldSpec.builder(delegateType, delegateFieldName);
              delegateFieldNames.put(resolvedBindings.key(), delegateFieldName);
              final FieldSpec fieldSpec = builder.build();
              classBuilder.addField(fieldSpec);
              final String methodName = "with" + delegateType.simpleName().toString();
              classBuilder.addMethod(MethodSpec.methodBuilder(methodName)
                      .addModifiers(Modifier.PUBLIC)
                      .returns(generatedTypeName)
                      .addParameter(delegateType, "delegate")
                      .addStatement("this.$N = delegate", fieldSpec)
                      .addStatement("return this")
                      .build());
          }
      }catch(Exception e) {}
  }
}
