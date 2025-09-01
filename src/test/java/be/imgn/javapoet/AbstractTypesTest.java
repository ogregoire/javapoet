/*
 * Copyright (C) 2014 Google, Inc.
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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static javax.lang.model.util.ElementFilter.fieldsIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.testing.compile.JavaFileObjects;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CompilationExtension.class)
public abstract class AbstractTypesTest {

  private Elements elements;
  private Types types;

  @BeforeEach
  public void setUp(Elements elements, Types types) {
    this.elements = elements;
    this.types = types;
  }

  protected final Elements getElements() {
    return elements;
  }

  protected final Types getTypes() {
    return types;
  }

  private TypeElement getElement(Class<?> clazz) {
    return getElements().getTypeElement(clazz.getCanonicalName());
  }

  private TypeMirror getMirror(Class<?> clazz) {
    return getElement(clazz).asType();
  }

  @Test public void getBasicTypeMirror() {
    assertThat(TypeName.get(getMirror(Object.class)))
        .isEqualTo(ClassName.get(Object.class));
    assertThat(TypeName.get(getMirror(Charset.class)))
        .isEqualTo(ClassName.get(Charset.class));
    assertThat(TypeName.get(getMirror(AbstractTypesTest.class)))
        .isEqualTo(ClassName.get(AbstractTypesTest.class));
  }

  @Test public void getParameterizedTypeMirror() {
    var setType =
        getTypes().getDeclaredType(getElement(Set.class), getMirror(Object.class));
    assertThat(TypeName.get(setType))
        .isEqualTo(ParameterizedTypeName.get(ClassName.get(Set.class), ClassName.OBJECT));
  }

  @Test public void errorTypes() {
    var hasErrorTypes =
        JavaFileObjects.forSourceLines(
            "be.imgn.tacos.ErrorTypes",
            "package be.imgn.tacos;",
            "",
            "@SuppressWarnings(\"hook-into-compiler\")",
            "class ErrorTypes {",
            "  Tacos tacos;",
            "  Ingredients.Guacamole guacamole;",
            "}");
    var compilation = javac().withProcessors(new AbstractProcessor() {
      @Override
      public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        var classFile =
            processingEnv.getElementUtils().getTypeElement("be.imgn.tacos.ErrorTypes");
        var fields = fieldsIn(classFile.getEnclosedElements());
        var topLevel = (ErrorType) fields.get(0).asType();
        var member = (ErrorType) fields.get(1).asType();

        assertThat(TypeName.get(topLevel)).isEqualTo(ClassName.get("", "Tacos"));
        assertThat(TypeName.get(member)).isEqualTo(ClassName.get("Ingredients", "Guacamole"));
        return false;
      }

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton("*");
      }
    }).compile(hasErrorTypes);

    assertThat(compilation).failed();
  }

  static class Parameterized<
      Simple,
      ExtendsClass extends Number,
      ExtendsInterface extends Runnable,
      ExtendsTypeVariable extends Simple,
      Intersection extends Number & Runnable,
      IntersectionOfInterfaces extends Runnable & Serializable> {}

  @Test public void getTypeVariableTypeMirror() {
    var typeVariables = getElement(Parameterized.class).getTypeParameters();

    // Members of converted types use ClassName and not Class<?>.
    var number = ClassName.get(Number.class);
    var runnable = ClassName.get(Runnable.class);
    var serializable = ClassName.get(Serializable.class);

    assertThat(TypeName.get(typeVariables.get(0).asType()))
        .isEqualTo(TypeVariableName.get("Simple"));
    assertThat(TypeName.get(typeVariables.get(1).asType()))
        .isEqualTo(TypeVariableName.get("ExtendsClass", number));
    assertThat(TypeName.get(typeVariables.get(2).asType()))
        .isEqualTo(TypeVariableName.get("ExtendsInterface", runnable));
    assertThat(TypeName.get(typeVariables.get(3).asType()))
        .isEqualTo(TypeVariableName.get("ExtendsTypeVariable", TypeVariableName.get("Simple")));
    assertThat(TypeName.get(typeVariables.get(4).asType()))
        .isEqualTo(TypeVariableName.get("Intersection", number, runnable));
    assertThat(TypeName.get(typeVariables.get(5).asType()))
        .isEqualTo(TypeVariableName.get("IntersectionOfInterfaces", runnable, serializable));
    assertThat(((TypeVariableName) TypeName.get(typeVariables.get(4).asType())).bounds)
        .containsExactly(number, runnable);
  }

  static class Recursive<T extends Map<List<T>, Set<T[]>>> {}

  @Test
  public void getTypeVariableTypeMirrorRecursive() {
    var typeMirror = getElement(Recursive.class).asType();
    var typeName = (ParameterizedTypeName) TypeName.get(typeMirror);
    var className = Recursive.class.getCanonicalName();
    assertThat(typeName)
      .hasToString(className + "<T>");

    var typeVariableName = (TypeVariableName) typeName.typeArguments.get(0);

    assertThatThrownBy(() -> {
      typeVariableName.bounds.set(0, null);
    })
      .isInstanceOf(UnsupportedOperationException.class);

    assertThat(typeVariableName)
      .hasToString("T");
    assertThat(typeVariableName.bounds.toString())
        .isEqualTo("[java.util.Map<java.util.List<T>, java.util.Set<T[]>>]");
  }

  @Test public void getPrimitiveTypeMirror() {
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.BOOLEAN)))
        .isEqualTo(TypeName.BOOLEAN);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.BYTE)))
        .isEqualTo(TypeName.BYTE);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.SHORT)))
        .isEqualTo(TypeName.SHORT);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.INT)))
        .isEqualTo(TypeName.INT);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.LONG)))
        .isEqualTo(TypeName.LONG);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.CHAR)))
        .isEqualTo(TypeName.CHAR);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.FLOAT)))
        .isEqualTo(TypeName.FLOAT);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.DOUBLE)))
        .isEqualTo(TypeName.DOUBLE);
  }

  @Test public void getArrayTypeMirror() {
    assertThat(TypeName.get(getTypes().getArrayType(getMirror(Object.class))))
        .isEqualTo(ArrayTypeName.of(ClassName.OBJECT));
  }

  @Test public void getVoidTypeMirror() {
    assertThat(TypeName.get(getTypes().getNoType(TypeKind.VOID)))
        .isEqualTo(TypeName.VOID);
  }

  @Test public void getNullTypeMirror() {
    assertThatThrownBy(() -> {
      TypeName.get(getTypes().getNullType());
    })
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test public void parameterizedType() throws Exception {
    var type = ParameterizedTypeName.get(Map.class, String.class, Long.class);
    assertThat(type)
      .hasToString("java.util.Map<java.lang.String, java.lang.Long>");
  }

  @Test public void arrayType() throws Exception {
    var type = ArrayTypeName.of(String.class);
    assertThat(type)
      .hasToString("java.lang.String[]");
  }

  @Test public void wildcardExtendsType() throws Exception {
    var type = WildcardTypeName.subtypeOf(CharSequence.class);
    assertThat(type)
      .hasToString("? extends java.lang.CharSequence");
  }

  @Test public void wildcardExtendsObject() throws Exception {
    var type = WildcardTypeName.subtypeOf(Object.class);
    assertThat(type)
      .hasToString("?");
  }

  @Test public void wildcardSuperType() throws Exception {
    var type = WildcardTypeName.supertypeOf(String.class);
    assertThat(type)
      .hasToString("? super java.lang.String");
  }

  @Test public void wildcardMirrorNoBounds() throws Exception {
    var wildcard = getTypes().getWildcardType(null, null);
    var type = TypeName.get(wildcard);
    assertThat(type)
      .hasToString("?");
  }

  @Test public void wildcardMirrorExtendsType() throws Exception {
    var types = getTypes();
    var elements = getElements();
    var charSequence = elements.getTypeElement(CharSequence.class.getName()).asType();
    var wildcard = types.getWildcardType(charSequence, null);
    var type = TypeName.get(wildcard);
    assertThat(type)
      .hasToString("? extends java.lang.CharSequence");
  }

  @Test public void wildcardMirrorSuperType() throws Exception {
    var types = getTypes();
    var elements = getElements();
    var string = elements.getTypeElement(String.class.getName()).asType();
    var wildcard = types.getWildcardType(null, string);
    var type = TypeName.get(wildcard);
    assertThat(type)
      .hasToString("? super java.lang.String");
  }

  @Test public void typeVariable() throws Exception {
    var type = TypeVariableName.get("T", CharSequence.class);
    assertThat(type)
      .hasToString("T"); // (Bounds are only emitted in declaration.)
  }

  @Test public void box() throws Exception {
    assertThat(TypeName.INT.box()).isEqualTo(ClassName.get(Integer.class));
    assertThat(TypeName.VOID.box()).isEqualTo(ClassName.get(Void.class));
    assertThat(ClassName.get(Integer.class).box()).isEqualTo(ClassName.get(Integer.class));
    assertThat(ClassName.get(Void.class).box()).isEqualTo(ClassName.get(Void.class));
    assertThat(ClassName.OBJECT.box()).isEqualTo(ClassName.OBJECT);
    assertThat(ClassName.get(String.class).box()).isEqualTo(ClassName.get(String.class));
  }

  @Test public void unbox() throws Exception {
    assertThat(TypeName.INT).isEqualTo(TypeName.INT.unbox());
    assertThat(TypeName.VOID).isEqualTo(TypeName.VOID.unbox());
    assertThat(ClassName.get(Integer.class).unbox()).isEqualTo(TypeName.INT.unbox());
    assertThat(ClassName.get(Void.class).unbox()).isEqualTo(TypeName.VOID.unbox());
    assertThatThrownBy(() -> {
      ClassName.OBJECT.unbox();
    })
      .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> {
      ClassName.get(String.class).unbox();
    })
      .isInstanceOf(UnsupportedOperationException.class);
  }
}
