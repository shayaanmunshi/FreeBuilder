/*
 * Copyright 2014 Google Inc. All rights reserved.
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
package org.inferred.freebuilder.processor.util;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;

import org.inferred.freebuilder.processor.util.feature.EnvironmentFeatureSet;
import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.FeatureType;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

/** Convenience wrapper around the {@link Writer} instances returned by {@link Filer}. */
public class CompilationUnitWriter implements SourceBuilder, Closeable {

  private final Writer writer;
  private final ImportManager importManager;
  private final SourceBuilder source;

  /**
   * Returns a {@link CompilationUnitWriter} for {@code classToWrite}. The file preamble (package
   * and imports) will be generated automatically.
   *
   * @throws FilerException if a Filer guarantee is violated (see the {@link FilerException}
   *     JavaDoc for more information); propagated because this is often seen in GUIDE projects,
   *     so should be downgraded to a warning, whereas runtime exceptions should be flagged as an
   *     internal error to the user
   */
  public CompilationUnitWriter(
      ProcessingEnvironment env,
      QualifiedName classToWrite,
      Collection<QualifiedName> nestedClasses,
      Element originatingElement) throws FilerException {
    try {
      writer = env.getFiler()
          .createSourceFile(classToWrite.toString(), originatingElement)
          .openWriter();
      writer
          .append("// Autogenerated code. Do not modify.\n")
          .append("package ").append(classToWrite.getPackage()).append(";\n")
          .append("\n");
    } catch (FilerException e) {
      throw e;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // Write the source code into an intermediate SourceStringBuilder, as the imports need to be
    // written first, but aren't known yet.
    ImportManager.Builder importManagerBuilder = new ImportManager.Builder();
    importManagerBuilder.addImplicitImport(classToWrite);
    PackageElement pkg = env.getElementUtils().getPackageElement(classToWrite.getPackage());
    for (TypeElement sibling : ElementFilter.typesIn(pkg.getEnclosedElements())) {
      importManagerBuilder.addImplicitImport(QualifiedName.of(sibling));
    }
    for (QualifiedName nestedClass : nestedClasses) {
      importManagerBuilder.addImplicitImport(nestedClass);
    }
    importManager = importManagerBuilder.build();
    source = new SourceStringBuilder(importManager, new EnvironmentFeatureSet(env));
  }

  @Override
  public CompilationUnitWriter add(String fmt, Object... args) {
    source.add(fmt, args);
    return this;
  }

  @Override
  public SourceBuilder add(Excerpt excerpt) {
    source.add(excerpt);
    return this;
  }

  @Override
  public CompilationUnitWriter addLine(String fmt, Object... args) {
    source.addLine(fmt, args);
    return this;
  }

  @Override
  public SourceStringBuilder subBuilder() {
    return source.subBuilder();
  }

  @Override
  public <T extends Feature<T>> T feature(FeatureType<T> feature) {
    return source.feature(feature);
  }

  @Override
  public void close() {
    try {
      if (!importManager.getClassImports().isEmpty()) {
        for (String classImport : importManager.getClassImports()) {
          writer.append("import ").append(classImport).append(";\n");
        }
        writer.append("\n");
      }
      writer.append(new Formatter().formatSource(source.toString()));
      writer.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (FormatterException e) {
      throw new RuntimeException(e);
    }
  }
}