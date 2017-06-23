/*
 * Copyright (C) 2014 The Dagger Authors.
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

import com.google.common.base.Joiner;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import dagger.Component;

import java.io.IOException;
import java.util.Optional;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Generates the implementation of the abstract types annotated with {@link Component}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ComponentGenerator extends SourceFileGenerator<BindingGraph> {
  private final Types types;
  private final Elements elements;
  private final Key.Factory keyFactory;
  private final CompilerOptions compilerOptions;
  private final TestRegistry testRegistry;
  private final AppConfig.Provider appConfigProvider;

  private ComponentGenerator(
          Filer filer,
          Elements elements,
          Types types,
          Key.Factory keyFactory,
          CompilerOptions compilerOptions,
          TestRegistry testRegistry,
          AppConfig.Provider appConfigProvider) {
    super(filer, elements);
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.compilerOptions = compilerOptions;
    this.testRegistry = testRegistry;
    this.appConfigProvider = appConfigProvider;
  }

  public static class Factory {
    private final Filer filer;
    private final Elements elements;
    private final Types types;
    private final Key.Factory keyFactory;
    private final CompilerOptions compilerOptions;
    private final AppConfig.Provider appConfigProvider;
    private final TestRegistry testRegistry;

    public Factory(Filer filer, Elements elements, Types types, Key.Factory keyFactory, CompilerOptions compilerOptions,
                   AppConfig.Provider appConfigProvider,
                   TestRegistry testRegistry) {
      this.filer = filer;
      this.elements = elements;
      this.types = types;
      this.keyFactory = keyFactory;
      this.compilerOptions = compilerOptions;
      this.appConfigProvider = appConfigProvider;
      this.testRegistry = testRegistry;
    }
    public ComponentGenerator createComponentGenerator() {
      return new ComponentGenerator(filer, elements, types, keyFactory, compilerOptions, testRegistry, appConfigProvider);
    }
  }

  @Override
  ClassName nameGeneratedType(BindingGraph input) {
    ClassName componentDefinitionClassName = ClassName.get(input.componentType());
    return Util.getDaggerComponentClassName(componentDefinitionClassName);
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(BindingGraph input) {
    return Optional.of(input.componentType());
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName componentName, BindingGraph input) {
    if (appConfigProvider.get().debug()) {
      final ClassName name = componentName.topLevelClassName().peerClass("Test" + Joiner.on('_').join(componentName.simpleNames()));
      System.out.println("generating test dagger component");
      final TypeSpec.Builder testComponentBuilder =
              new ComponentWriter(types, elements, keyFactory, compilerOptions, name, input, true).write();
      System.out.println("DONE generating test dagger component");
      try {
        System.out.println("adding to registry");
        testRegistry.addEncodedClass(name, buildJavaFile(name, testComponentBuilder));
        System.out.println("DONE - adding to registry");
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    return Optional.of(
        new ComponentWriter(types, elements, keyFactory, compilerOptions, componentName, input, false).write()
    );
  }
}
