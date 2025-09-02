/*
 * Copyright (C) 2015 Square, Inc.
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
package be.imgn.javapoet;

import java.util.List;
import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Types;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static be.imgn.javapoet.TestUtil.findFirst;
import static javax.lang.model.util.ElementFilter.fieldsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@ExtendWith(CompilationExtension.class)
public class ParameterSpecTest {

  private Elements elements;
  private Types types;

  @BeforeEach
  public void setUp(Elements elements, Types types) {
    this.elements = elements;
    this.types = types;
  }

  private TypeElement getElement(Class<?> clazz) {
    return elements.getTypeElement(clazz.getCanonicalName());
  }

  @Test public void equalsAndHashCode() {
    var a = ParameterSpec.builder(int.class, "foo").build();
    var b = ParameterSpec.builder(int.class, "foo").build();
    assertThat(a)
      .isEqualTo(b)
      .hasSameHashCodeAs(b)
      .hasToString(b.toString());
    a = ParameterSpec.builder(int.class, "i").addModifiers(Modifier.STATIC).build();
    b = ParameterSpec.builder(int.class, "i").addModifiers(Modifier.STATIC).build();
    assertThat(a)
      .isEqualTo(b)
      .hasSameHashCodeAs(b)
      .hasToString(b.toString());
  }

  @Test public void receiverParameterInstanceMethod() {
    var builder = ParameterSpec.builder(int.class, "this");
    assertThat(builder.build().name())
      .isEqualTo("this");
  }

  @Test public void receiverParameterNestedClass() {
    var builder = ParameterSpec.builder(int.class, "Foo.this");
    assertThat(builder.build().name())
      .isEqualTo("Foo.this");
  }

  @Test public void keywordName() {
    assertThatThrownBy(() -> ParameterSpec.builder(int.class, "super"))
      .hasMessage("not a valid name: super");
  }

  @Test public void nullAnnotationsAddition() {
    assertThatThrownBy(() -> ParameterSpec.builder(int.class, "foo").addAnnotations(null))
      .hasMessage("annotationSpecs == null");
  }

  final class VariableElementFieldClass {
    String name;
  }

  @Test public void fieldVariableElement() {
    var classElement = getElement(VariableElementFieldClass.class);
    var methods = fieldsIn(elements.getAllMembers(classElement));
    var element = findFirst(methods, "name");

    assertThatThrownBy(() -> ParameterSpec.get(element))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("element is not a parameter");
  }

  final class VariableElementParameterClass {
    public void foo(@Nullable final String bar) {
    }
  }

  @Test public void parameterVariableElement() {
    var classElement = getElement(VariableElementParameterClass.class);
    var methods = methodsIn(elements.getAllMembers(classElement));
    var element = findFirst(methods, "foo");
    var parameterElement = element.getParameters().getFirst();

    assertThat(ParameterSpec.get(parameterElement).toString())
        .isEqualTo("java.lang.String bar");
  }

  @Test public void addNonFinalModifier() {
    var modifiers = List.of(
      Modifier.FINAL,
      Modifier.PUBLIC);

    assertThatThrownBy(() -> ParameterSpec.builder(int.class, "foo").addModifiers(modifiers))
      .hasMessage("unexpected parameter modifier: public");
  }

  @Test public void modifyAnnotations() {
    var builder = ParameterSpec.builder(int.class, "foo")
            .addAnnotation(Override.class)
            .addAnnotation(SuppressWarnings.class);

    builder.annotations.remove(1);
    assertThat(builder.build().annotations())
      .hasSize(1);
  }

  @Test public void modifyModifiers() {
    var builder = ParameterSpec.builder(int.class, "foo")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

    builder.modifiers.remove(1);
    assertThat(builder.build().modifiers())
      .containsExactly(Modifier.PUBLIC);
  }
}
