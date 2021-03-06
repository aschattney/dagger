/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.android.processor;

import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import dagger.Binds;
import dagger.Module;
import dagger.Subcomponent;
import dagger.Subcomponent.Builder;
import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;
import dagger.multibindings.IntoMap;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.ElementFilter;

/** Generates the implementation specified in {@link ContributesAndroidInjector}. */
final class ContributesAndroidInjectorGenerator implements ProcessingStep {

  private final Filer filer;
  private final AndroidInjectorDescriptor.Validator validator;

  ContributesAndroidInjectorGenerator(Filer filer, AndroidInjectorDescriptor.Validator validator) {
    this.filer = filer;
    this.validator = validator;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(ContributesAndroidInjector.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (ExecutableElement method : ElementFilter.methodsIn(elementsByAnnotation.values())) {
      validator.createIfValid(method).ifPresent(this::generate);
    }
    return ImmutableSet.of();
  }

  private void generate(AndroidInjectorDescriptor descriptor) {
    ClassName moduleName =
        descriptor
            .enclosingModule()
            .topLevelClassName()
            .peerClass(
                Joiner.on('_').join(descriptor.enclosingModule().simpleNames())
                    + "_"
                    + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, descriptor.methodName()));

    String baseName = descriptor.injectedType().simpleName();
    ClassName subcomponentName = moduleName.nestedClass(baseName + "Subcomponent");
    ClassName subcomponentBuilderName = subcomponentName.nestedClass("Builder");

    TypeSpec module =
        TypeSpec.classBuilder(moduleName)
            .addModifiers(PUBLIC, ABSTRACT)
            .addAnnotation(
                AnnotationSpec.builder(Module.class)
                    .addMember("subcomponents", "$T.class", subcomponentName)
                    .build())
            .addMethod(bindAndroidInjectorFactory(descriptor, subcomponentBuilderName))
            .addType(subcomponent(descriptor, subcomponentName, subcomponentBuilderName))
            .addMethod(MethodSpec.constructorBuilder().addModifiers(PRIVATE).build())
            .build();
    try {
      JavaFile.builder(moduleName.packageName(), module)
          .skipJavaLangImports(true)
          .build()
          .writeTo(filer);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private MethodSpec bindAndroidInjectorFactory(
      AndroidInjectorDescriptor descriptor, ClassName subcomponentBuilderName) {
    return MethodSpec.methodBuilder("bindAndroidInjectorFactory")
        .addAnnotation(Binds.class)
        .addAnnotation(IntoMap.class)
        .addAnnotation(descriptor.mapKeyAnnotation())
        .addModifiers(ABSTRACT)
        .returns(
            parameterizedTypeName(
                AndroidInjector.Factory.class,
                WildcardTypeName.subtypeOf(descriptor.frameworkType())))
        .addParameter(subcomponentBuilderName, "builder")
        .build();
  }

  private TypeSpec subcomponent(
      AndroidInjectorDescriptor descriptor,
      ClassName subcomponentName,
      ClassName subcomponentBuilderName) {
    AnnotationSpec.Builder subcomponentAnnotation = AnnotationSpec.builder(Subcomponent.class);
    for (ClassName module : descriptor.modules()) {
      subcomponentAnnotation.addMember("modules", CodeBlock.of("$T.class", module));
    }

    return TypeSpec.interfaceBuilder(subcomponentName)
        .addModifiers(PUBLIC)
        .addAnnotation(subcomponentAnnotation.build())
        .addAnnotations(descriptor.scopes())
        .addSuperinterface(parameterizedTypeName(AndroidInjector.class, descriptor.injectedType()))
        .addType(subcomponentBuilder(descriptor, subcomponentName, subcomponentBuilderName))
        .build();
  }

  private MethodSpec overrideBuildMethod(ClassName subcomponentName) {
    return MethodSpec.methodBuilder("build")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(Override.class)
            .returns(subcomponentName)
            .build();
  }

  private TypeSpec subcomponentBuilder(
          AndroidInjectorDescriptor descriptor, ClassName subcomponentName, ClassName subcomponentBuilderName) {
    return TypeSpec.classBuilder(subcomponentBuilderName)
        .addAnnotation(Builder.class)
        .addModifiers(PUBLIC, ABSTRACT, STATIC)
        .addMethod(overrideBuildMethod(subcomponentName))
        .superclass(parameterizedTypeName(AndroidInjector.Builder.class, descriptor.injectedType()))
        .build();
  }

  private static ParameterizedTypeName parameterizedTypeName(
      Class<?> clazz, TypeName... typeArguments) {
    return ParameterizedTypeName.get(ClassName.get(clazz), typeArguments);
  }
}
