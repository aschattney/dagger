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
import static dagger.internal.codegen.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.SourceFiles.simpleVariableName;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import java.util.Optional;

import com.google.common.collect.*;
import com.squareup.javapoet.*;
import dagger.*;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntKey;
import dagger.multibindings.LongKey;
import dagger.multibindings.StringKey;
import dagger.producers.Produces;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.stream.Collector;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import javax.inject.Named;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;
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

    /**
     * A function that returns the input as a {@link DeclaredType}.
     */
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
/*
      case MEMBER:
        return !typeElement.getModifiers().contains(STATIC);
      case ANONYMOUS:
      case LOCAL:
        return true;
      default:
        throw new AssertionError(
            "TypeElement cannot have nesting kind: " + typeElement.getNestingKind());
    }*/
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

    /**
     * A function that returns the simple name of an element.
     */
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

    public static String toParameterName(String simpleName) {
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    public static String extractPackage(TypeMirror classType) {
        return classType.toString().replaceAll("." + convertDataClassToString(classType), "");
    }

    public static String extractClassName(TypeMirror classType) {
        return convertDataClassToString(classType);
    }

    public static String extractClassName(String s) {
        int index = s.lastIndexOf(".");
        return s.substring(index + 1);
    }

    public static String convertDataClassToString(TypeMirror dataClass) {
        String s = dataClass.toString();
        int index = s.lastIndexOf(".");
        return s.substring(index + 1);
    }

    static ClassName getDelegateTypeName(Key key) {

        if (key.multibindingContributionIdentifier().isPresent()) {
            final Key.MultibindingContributionIdentifier identifier = key.multibindingContributionIdentifier().get();
            return identifier.getDelegateTypeName();
        }

        final TypeMirror returnType = key.type();

        // find qualifier annotations
        final Optional<? extends AnnotationMirror> qualifier = key.qualifier();

        String simpleQualifierName = "";
        String simpleQualifierValue = "";

        if (qualifier.isPresent()) {
            Optional<String> qualifierValue = qualifier.get().getElementValues().entrySet().stream()
                    .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                    .filter(e -> e.getKey().getReturnType().toString().equals(String.class.getName()))
                    .map(e -> e.getValue().getValue().toString())
                    .findFirst();
            simpleQualifierName = MoreAnnotationMirrors.simpleName(qualifier.get()).toString();
            if (qualifierValue.isPresent()) {
                simpleQualifierValue = qualifierValue.get();
            }
        }

        StringBuilder sb = new StringBuilder();
        if (qualifier.isPresent()) {
            sb.append(capitalize(simpleQualifierName).trim());
            if (!simpleQualifierValue.trim().isEmpty()) {
                sb.append(capitalize(simpleQualifierValue).trim());
            }else {
                sb.append(capitalize(extractClassName(typeToString(returnType))).trim());
            }
        }else {
            sb.append(capitalize(extractClassName(typeToString(returnType))).trim());
        }

        final ClassName name = ClassName.bestGuess(String.format("delegates.%sDelegate", sb.toString()));
        return name;
    }

    static ClassName getMockTypeName(Key key) {

        if (key.multibindingContributionIdentifier().isPresent()) {
            final Key.MultibindingContributionIdentifier identifier = key.multibindingContributionIdentifier().get();
            return identifier.getMockTypeName();
        }

        final TypeMirror returnType = key.type();

        // find qualifier annotations
        final Optional<? extends AnnotationMirror> qualifier = key.qualifier();

        String simpleQualifierName = "";
        String simpleQualifierValue = "";

        if (qualifier.isPresent()) {
            Optional<String> qualifierValue = qualifier.get().getElementValues().entrySet().stream()
                    .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                    .filter(e -> e.getKey().getReturnType().toString().equals(String.class.getName()))
                    .map(e -> e.getValue().getValue().toString())
                    .findFirst();
            simpleQualifierName = MoreAnnotationMirrors.simpleName(qualifier.get()).toString();
            if (qualifierValue.isPresent()) {
                simpleQualifierValue = qualifierValue.get();
            }
        }

        StringBuilder sb = new StringBuilder();
        if (qualifier.isPresent()) {
            sb.append(capitalize(simpleQualifierName).trim());
            if (!simpleQualifierValue.trim().isEmpty()) {
                sb.append(capitalize(simpleQualifierValue).trim());
            }else {
                sb.append(capitalize(extractClassName(typeToString(returnType))).trim());
            }
        }else {
            sb.append(capitalize(extractClassName(typeToString(returnType))).trim());
        }

        final ClassName name = ClassName.bestGuess(String.format("delegates.%sMock", sb.toString()));
        return name;
    }

    static String getDelegateFieldName(Key key) {

        if (key.multibindingContributionIdentifier().isPresent()) {
            final Key.MultibindingContributionIdentifier identifier = key.multibindingContributionIdentifier().get();
            return identifier.getDelegateFieldName();
        }

        final TypeMirror returnType = key.type();

        // find qualifier annotations
        final Optional<? extends AnnotationMirror> qualifier = key.qualifier();

        Optional<String> qualifierValue = Optional.empty();

        String simpleQualifierName = "";
        String simpleQualifierValue = "";

        if (qualifier.isPresent()) {
            qualifierValue = qualifier.get().getElementValues().entrySet().stream()
                    .filter(e -> e.getKey().getSimpleName().toString().equals("value"))
                    .filter(e -> e.getKey().getReturnType().toString().equals(String.class.getName()))
                    .map(e -> e.getValue().getValue().toString())
                    .findFirst();
            simpleQualifierName = MoreAnnotationMirrors.simpleName(qualifier.get()).toString();
            if (qualifierValue.isPresent()) {
                simpleQualifierValue = qualifierValue.get();
            }
        }

        StringBuilder sb = new StringBuilder();
        if (qualifier.isPresent()) {
            sb.append(lowerCaseFirstLetter(simpleQualifierName));
            if (!simpleQualifierValue.isEmpty()) {
                sb.append(transformValue(simpleQualifierValue, sb));
            }else {
                sb.append(transformValue(extractClassName(typeToString(returnType)), sb));
            }
        }else {
            sb.append(lowerCaseFirstLetter(extractClassName(typeToString(returnType))));
        }

        sb.append("Delegate");

        return sb.toString();
    }

    static String getMockFieldName(Key key) {

        if (key.multibindingContributionIdentifier().isPresent()) {
            final Key.MultibindingContributionIdentifier identifier = key.multibindingContributionIdentifier().get();
            return identifier.getMockFieldName();
        }

        final TypeMirror returnType = key.type();

        // find qualifier annotations
        final Optional<? extends AnnotationMirror> qualifier = key.qualifier();

        Optional<String> qualifierValue = Optional.empty();

        String simpleQualifierName = "";
        String simpleQualifierValue = "";

        if (qualifier.isPresent()) {
            qualifierValue = qualifier.get().getElementValues().entrySet().stream()
                    .filter(e -> e.getKey().getSimpleName().toString().equals("value"))
                    .filter(e -> e.getKey().getReturnType().toString().equals(String.class.getName()))
                    .map(e -> e.getValue().getValue().toString())
                    .findFirst();
            simpleQualifierName = MoreAnnotationMirrors.simpleName(qualifier.get()).toString();
            if (qualifierValue.isPresent()) {
                simpleQualifierValue = qualifierValue.get();
            }
        }

        StringBuilder sb = new StringBuilder();
        if (qualifier.isPresent()) {
            sb.append(lowerCaseFirstLetter(simpleQualifierName));
            if (!simpleQualifierValue.isEmpty()) {
                sb.append(transformValue(simpleQualifierValue, sb));
            }else {
                sb.append(transformValue(extractClassName(typeToString(returnType)), sb));
            }
        }else {
            sb.append(lowerCaseFirstLetter(extractClassName(typeToString(returnType))));
        }

        sb.append("Mock");

        return sb.toString();
    }

    private static String transformValue(String simpleMapValueName, StringBuilder sb) {
        return sb.length() == 0 ? lowerCaseFirstLetter(simpleMapValueName) : capitalize(simpleMapValueName);
    }

    /**
     * Returns a string for {@code type}. Primitive types are always boxed.
     */
    public static String typeToString(TypeMirror type) {
        StringBuilder result = new StringBuilder();
        typeToString(type, result, '.', false);
        return result.toString();
    }

    /**
     * Returns a string for the raw type of {@code type}. Primitive types are always boxed.
     */
    public static String rawTypeToString(TypeMirror type, char innerClassSeparator) {
        if (!(type instanceof DeclaredType)) {
            throw new IllegalArgumentException("Unexpected type: " + type);
        }
        StringBuilder result = new StringBuilder();
        DeclaredType declaredType = (DeclaredType) type;
        rawTypeToString(result, (TypeElement) declaredType.asElement(), innerClassSeparator, false);
        return result.toString();
    }

    public static PackageElement getPackage(Element type) {
        while (type.getKind() != ElementKind.PACKAGE) {
            type = type.getEnclosingElement();
        }
        return (PackageElement) type;
    }

    static void rawTypeToString(StringBuilder result, TypeElement type, char innerClassSeparator, boolean typeParam) {
        if (typeParam) {
            final String s = MoreElements.asType(type).getSimpleName().toString();
            result.append(s);
            return;
        }

        String packageName = getPackage(type).getQualifiedName().toString();
        String qualifiedName = type.getQualifiedName().toString();
        if (packageName.isEmpty()) {
            result.append(qualifiedName.replace('.', innerClassSeparator));
        } else {
            result.append(packageName);
            result.append('.');
            result.append(
                    qualifiedName.substring(packageName.length() + 1).replace('.', innerClassSeparator));
        }
    }

    /**
     * Appends a string for {@code type} to {@code result}. Primitive types are
     * always boxed.
     *
     * @param innerClassSeparator either '.' or '$', which will appear in a
     *                            class name like "java.lang.Map.Entry" or "java.lang.Map$Entry".
     *                            Use '.' for references to existing types in code. Use '$' to define new
     *                            class names and for strings that will be used by runtime reflection.
     */
    public static void typeToString(final TypeMirror type, final StringBuilder result, final char innerClassSeparator, boolean typeParam) {
        type.accept(new SimpleTypeVisitor6<Void, Void>() {
            @Override
            public Void visitDeclared(DeclaredType declaredType, Void v) {
                TypeElement typeElement = (TypeElement) declaredType.asElement();
                rawTypeToString(result, typeElement, innerClassSeparator, typeParam);
                List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
                if (!typeArguments.isEmpty()) {
                    result.append("Of");
                    for (int i = 0; i < typeArguments.size(); i++) {
                        if (i != 0) {
                            result.append("And");
                        }
                        typeToString(typeArguments.get(i), result, '\0', true);
                    }
                }
                return null;
            }

            @Override
            public Void visitPrimitive(PrimitiveType primitiveType, Void v) {
                result.append(box((PrimitiveType) type));
                return null;
            }

            @Override
            public Void visitArray(ArrayType arrayType, Void v) {
                TypeMirror type = arrayType.getComponentType();
                if (type instanceof PrimitiveType) {
                    result.append("ArrayOf" + extractClassName(type)); // Don't box, since this is an array.
                } else {
                    typeToString(arrayType.getComponentType(), result, innerClassSeparator, true);
                }
                result.append("_");
                return null;
            }

            @Override
            public Void visitTypeVariable(TypeVariable typeVariable, Void v) {
                result.append(typeVariable.asElement().getSimpleName());
                return null;
            }

            @Override
            public Void visitWildcard(WildcardType t, Void aVoid) {
                return null;
            }

            @Override
            public Void visitError(ErrorType errorType, Void v) {
                // Error type found, a type may not yet have been generated, but we need the type
                // so we can generate the correct code in anticipation of the type being available
                // to the compiler.

                // Paramterized types which don't exist are returned as an error type whose name is "<any>"
                if ("<any>".equals(errorType.toString())) {
                    throw new IllegalStateException(
                            "Type reported as <any> is likely a not-yet generated parameterized type.");
                }
                // TODO(cgruber): Figure out a strategy for non-FQCN cases.
                result.append(errorType.toString());
                return null;
            }

            @Override
            protected Void defaultAction(TypeMirror typeMirror, Void v) {
                throw new UnsupportedOperationException(
                        "Unexpected TypeKind " + typeMirror.getKind() + " for " + typeMirror);
            }
        }, null);
    }

    static TypeName box(PrimitiveType primitiveType) {
        switch (primitiveType.getKind()) {
            case BYTE:
                return ClassName.get(Byte.class);
            case SHORT:
                return ClassName.get(Short.class);
            case INT:
                return ClassName.get(Integer.class);
            case LONG:
                return ClassName.get(Long.class);
            case FLOAT:
                return ClassName.get(Float.class);
            case DOUBLE:
                return ClassName.get(Double.class);
            case BOOLEAN:
                return ClassName.get(Boolean.class);
            case CHAR:
                return ClassName.get(Character.class);
            case VOID:
                return ClassName.get(Void.class);
            default:
                throw new AssertionError();
        }
    }

    public static String capitalize(String original) {
        if (original == null || original.length() == 0) {
            return original;
        }
        return original.substring(0, 1).toUpperCase() + original.substring(1);
    }

    public static String lowerCaseFirstLetter(String original) {
        if (original == null || original.length() == 0) {
            return original;
        }
        return original.substring(0, 1).toLowerCase() + original.substring(1);
    }

    public static boolean bindingCanBeProvidedInTest(ContributionBinding binding) {
        final ImmutableList<ContributionBinding.Kind> kinds = ImmutableList.of(
                ContributionBinding.Kind.PROVISION,
                ContributionBinding.Kind.INJECTION,
                ContributionBinding.Kind.BUILDER_BINDING,
                ContributionBinding.Kind.SYNTHETIC_DELEGATE_BINDING
        );
        final ContributionBinding.Kind kind = binding.bindingKind();
        return kinds.contains(kind);
    }

    public static boolean bindingSupportsTestDelegate(ContributionBinding binding) {
        return bindingCanBeProvidedInTest(binding) && !binding.genericParameter() && !binding.ignoreStubGeneration();
    }

    private Util() {
    }

    public static void createDelegateMethod(TypeName generatedTypeName, TypeSpec.Builder classBuilder, ContributionBinding binding) {
        try {
            if (bindingSupportsTestDelegate(binding)) {
                final String delegateFieldName = Util.getDelegateFieldName(binding.key());
                final ClassName delegateType = getDelegateTypeName(binding.key());
                final String methodName = getDelegateMethodName(delegateType);
                final MethodSpec.Builder delegateMethodBuilder = MethodSpec.methodBuilder(methodName);
                delegateMethodBuilder.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
                classBuilder.addMethod(delegateMethodBuilder
                        .returns(generatedTypeName)
                        .addParameter(delegateType, delegateFieldName)
                        .build());
            }
        } catch (Exception e) {
        }
    }

    public static void createMockInstanceMethod(TypeName generatedTypeName, TypeSpec.Builder classBuilder, ContributionBinding binding) {
        try {
            if (bindingSupportsTestDelegate(binding)) {
                final ClassName delegateType = getDelegateTypeName(binding.key());
                final String methodName = getDelegateMethodName(delegateType);
                final MethodSpec.Builder delegateMethodBuilder = MethodSpec.methodBuilder(methodName);
                delegateMethodBuilder.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
                classBuilder.addMethod(delegateMethodBuilder
                        .addParameter(
                                ParameterizedTypeName.get(ClassName.get(Provider.class),
                                ClassName.get(binding.contributedType())), "provider"
                        ).returns(generatedTypeName)
                        .build());
            }
        } catch (Exception e) {
        }
    }

    public static void createMockMethodImplementation(TypeName generatedTypeName, TypeSpec.Builder classBuilder, ContributionBinding binding) {
        try {
            if (bindingSupportsTestDelegate(binding)) {
                final String mockFieldName = Util.getMockFieldName(binding.key());
                final ClassName delegateType = getMockTypeName(binding.key());
                final String methodName = getMockMethodName(delegateType);
                final TypeName providerType = providerOf(binding);
                final MethodSpec.Builder mockMethodBuilder = MethodSpec.methodBuilder(methodName);
                mockMethodBuilder.addModifiers(Modifier.PUBLIC);
                classBuilder.addMethod(mockMethodBuilder
                        .returns(generatedTypeName)
                        .addParameter(providerType, "provider", Modifier.FINAL)
                        .addStatement("this.$L = provider", mockFieldName)
                        .addStatement("return this")
                        .build());
            }
        } catch (Exception e) {
        }
    }

    protected static CodeBlock createParametersCodeBlock(ContributionBinding binding) {
        final ImmutableMap<BindingKey, FrameworkField> map = generateBindingFieldsForDependencies(binding);
        return binding.explicitDependencies()
                .stream()
                .map(request -> createCodeBlock(request, map))
                .collect(CodeBlocks.joiningCodeBlocks(","));
    }

    private static CodeBlock createCodeBlock(DependencyRequest request, ImmutableMap<BindingKey, FrameworkField> map) {
        FrameworkField field = map.get(request.bindingKey());
        TypeName type = request.kind() == DependencyRequest.Kind.INSTANCE ? field.type().typeArguments.get(0) : field.type();
        return CodeBlock.of("$T $L", type, field.name());

    }

    public static void createDelegateMethodImplementation(TypeName generatedTypeName, TypeSpec.Builder classBuilder, ContributionBinding binding) {
        try {
            if (bindingSupportsTestDelegate(binding)) {
                final String delegateFieldName = Util.getDelegateFieldName(binding.key());
                final ClassName delegateType = getDelegateTypeName(binding.key());
                final String methodName = getDelegateMethodName(delegateType);
                final MethodSpec.Builder delegateMethodBuilder = MethodSpec.methodBuilder(methodName);
                delegateMethodBuilder.addModifiers(Modifier.PUBLIC);
                classBuilder.addMethod(delegateMethodBuilder
                        .returns(generatedTypeName)
                        .addParameter(delegateType, delegateFieldName)
                        .addStatement("this.$L = $L", delegateFieldName, CodeBlock.of(delegateFieldName))
                        .addStatement("return this")
                        .build());
            }
        } catch (Exception e) {
        }
    }

    public static void createDelegateField(TypeSpec.Builder classBuilder, ContributionBinding binding) {
        try {
            if (bindingSupportsTestDelegate(binding)) {
                final String delegateFieldName = Util.getDelegateFieldName(binding.key());
                final ClassName delegateType = getDelegateTypeName(binding.key());
                final FieldSpec.Builder builder = FieldSpec.builder(delegateType, delegateFieldName);
                builder.addModifiers(Modifier.PRIVATE);
                final FieldSpec fieldSpec = builder.build();
                classBuilder.addField(fieldSpec);
            }
        } catch (Exception e) {}
    }

    public static void createDelegateFieldAndMethod(TypeName generatedTypeName, TypeSpec.Builder classBuilder, ContributionBinding binding, Map<Key, String> delegateFieldNames, boolean publicMethod) {
        try {
            if (bindingSupportsTestDelegate(binding)) {
                final String delegateFieldName = Util.getDelegateFieldName(binding.key());
                final ClassName delegateType = getDelegateTypeName(binding.key());
                final FieldSpec.Builder builder = FieldSpec.builder(delegateType, delegateFieldName);
                builder.addModifiers(Modifier.PRIVATE);
                delegateFieldNames.put(binding.key(), delegateFieldName);
                final FieldSpec fieldSpec = builder.build();
                classBuilder.addField(fieldSpec);
                final String methodName = getDelegateMethodName(delegateType);
                final MethodSpec.Builder delegateMethodBuilder = MethodSpec.methodBuilder(methodName);
                if (publicMethod) {
                    delegateMethodBuilder.addModifiers(Modifier.PUBLIC);
                }
                classBuilder.addMethod(delegateMethodBuilder
                        .returns(generatedTypeName)
                        .addParameter(delegateType, delegateFieldName)
                        .addStatement("this.$N = $L", fieldSpec, CodeBlock.of(delegateFieldName))
                        .addStatement("return this")
                        .build());
            }
        } catch (Exception e) {
        }
    }

    public static void createMockFieldAndMethod(TypeName generatedTypeName, TypeSpec.Builder classBuilder, ContributionBinding binding, Map<Key, String> mockFieldNames, boolean publicMethod) {
        try {
            if (binding.dependencies().isEmpty()) {
                return;
            }
            if (bindingSupportsTestDelegate(binding)) {
                final String mockFieldName = Util.getMockFieldName(binding.key());
                final ClassName delegateType = Util.getMockTypeName(binding.key());
                final FieldSpec.Builder builder = FieldSpec.builder(providerOf(binding), mockFieldName);
                builder.addModifiers(Modifier.PRIVATE);
                mockFieldNames.put(binding.key(), mockFieldName);
                final FieldSpec fieldSpec = builder.build();
                classBuilder.addField(fieldSpec);
                final String methodName = getMockMethodName(delegateType);
                final MethodSpec.Builder delegateMethodBuilder = MethodSpec.methodBuilder(methodName);
                if (publicMethod) {
                    delegateMethodBuilder.addModifiers(Modifier.PUBLIC);
                }
                classBuilder.addMethod(delegateMethodBuilder
                        .returns(generatedTypeName)
                        .addParameter(providerOf(binding), mockFieldName)
                        .addStatement("this.$N = $L", fieldSpec, CodeBlock.of(mockFieldName))
                        .addStatement("return this")
                        .build());
            }
        } catch (Exception e) {
        }
    }

    static TypeName providerOf(ContributionBinding binding) {
        return ParameterizedTypeName.get(ClassName.get(Provider.class), ClassName.get(binding.contributedType()));
    }

    public static String getProvisionMethodName(ContributionBinding binding) {
        return "get" + getDelegateTypeName(binding.key()).simpleName().replaceAll("Delegate$", "");
    }

    public static String getDelegateMethodName(ClassName delegateType) {
        return "with" + delegateType.simpleName().replaceAll("Delegate$", "");
    }

    public static ClassName getDaggerComponentClassName(ClassName componentDefinitionClassName) {
       String componentName =
                "Dagger" + Joiner.on('_').join(componentDefinitionClassName.simpleNames());
        //componentDefinitionClassName = ClassName.bestGuess("factories." + componentName);
        return componentDefinitionClassName.topLevelClassName().peerClass(componentName);
    }

    public static ClassName getDaggerComponentClassName(Element component) {
        return getDaggerComponentClassName(ClassName.bestGuess(typeToString(component.asType())));
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object,Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    public static final String METHOD_NAME_GET_INJECTOR = "getInjector";
    public static final ClassName TYPENAME_INJECTOR = ClassName.bestGuess("injector.Injector");
    public static final String SIMPLE_NAME_INJECTOR_APPLICATION = "DaggerHookApplication";
    public static final String FIELDNAME_INJECTOR = "injector";
    public static final ClassName TYPENAME_INJECTOR_SPEC = ClassName.bestGuess("injector.InjectorSpec");
    public static final ClassName TYPENAME_ANDROID_APPLICATION = ClassName.bestGuess("android.app.Application");
    public static final ClassName TYPENAME_DAGGER_ANDROID_APPLICATION = TYPENAME_ANDROID_APPLICATION.topLevelClassName().peerClass("DaggerApplication");

    public static boolean generateTestDelegate(ContributionBinding binding) {
        return bindingCanBeProvidedInTest(binding) && binding.generateTestDelegate();
    }

    public static void createMockField(TypeSpec.Builder classBuilder, ContributionBinding binding) {
        try {
            if (bindingSupportsTestDelegate(binding)) {
                final String mockFieldName = Util.getMockFieldName(binding.key());
                final FieldSpec.Builder builder = FieldSpec.builder(providerOf(binding), mockFieldName);
                builder.addModifiers(Modifier.PRIVATE);
                final FieldSpec fieldSpec = builder.build();
                classBuilder.addField(fieldSpec);
            }
        } catch (Exception e) {}
    }

    static String getMockMethodName(ClassName mockTypeName) {
        return "with" + mockTypeName.simpleName().replaceAll("Delegate$", "").replaceAll("Mock$", "");
    }
}
