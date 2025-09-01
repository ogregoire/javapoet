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

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeNameTest {

  private static final AnnotationSpec ANNOTATION_SPEC = AnnotationSpec.builder(ClassName.OBJECT).build();

  protected <E extends Enum<E>> E generic(E[] values) {
    return values[0];
  }

  protected static class TestGeneric<T> {
    class Inner {}

    class InnerGeneric<T2> {}

    static class NestedNonGeneric {}
  }

  protected static TestGeneric<String>.Inner testGenericStringInner() {
    return null;
  }

  protected static TestGeneric<Integer>.Inner testGenericIntInner() {
    return null;
  }

  protected static TestGeneric<Short>.InnerGeneric<Long> testGenericInnerLong() {
    return null;
  }

  protected static TestGeneric<Short>.InnerGeneric<Integer> testGenericInnerInt() {
    return null;
  }

  protected static TestGeneric.NestedNonGeneric testNestedNonGeneric() {
    return null;
  }

  @Test public void genericType() throws Exception {
    var recursiveEnum = getClass().getDeclaredMethod("generic", Enum[].class);
    TypeName.get(recursiveEnum.getReturnType());
    TypeName.get(recursiveEnum.getGenericReturnType());
    var genericTypeName = TypeName.get(recursiveEnum.getParameterTypes()[0]);
    TypeName.get(recursiveEnum.getGenericParameterTypes()[0]);

    // Make sure the generic argument is present
    assertThat(genericTypeName.toString()).contains("Enum");
  }

  @Test public void innerClassInGenericType() throws Exception {
    var genericStringInner = getClass().getDeclaredMethod("testGenericStringInner");
    TypeName.get(genericStringInner.getReturnType());
    var genericTypeName = TypeName.get(genericStringInner.getGenericReturnType());
    assertThat(TypeName.get(genericStringInner.getGenericReturnType()))
      .isNotEqualTo(TypeName.get(getClass().getDeclaredMethod("testGenericIntInner").getGenericReturnType()));

    // Make sure the generic argument is present
    assertThat(genericTypeName)
      .hasToString(
        TestGeneric.class.getCanonicalName() + "<java.lang.String>.Inner");
  }

  @Test public void innerGenericInGenericType() throws Exception {
    var genericStringInner = getClass().getDeclaredMethod("testGenericInnerLong");
    TypeName.get(genericStringInner.getReturnType());
    var genericTypeName = TypeName.get(genericStringInner.getGenericReturnType());
    assertThat(TypeName.get(genericStringInner.getGenericReturnType()))
      .isNotEqualTo(TypeName.get(getClass().getDeclaredMethod("testGenericInnerInt").getGenericReturnType()));

    // Make sure the generic argument is present
    assertThat(genericTypeName)
      .hasToString(
        TestGeneric.class.getCanonicalName() + "<java.lang.Short>.InnerGeneric<java.lang.Long>");
  }

  @Test public void innerStaticInGenericType() throws Exception {
    var staticInGeneric = getClass().getDeclaredMethod("testNestedNonGeneric");
    TypeName.get(staticInGeneric.getReturnType());
    var typeName = TypeName.get(staticInGeneric.getGenericReturnType());

    // Make sure there are no generic arguments
    assertThat(typeName)
      .hasToString(
        TestGeneric.class.getCanonicalName() + ".NestedNonGeneric");
  }

  @ParameterizedTest
  @MethodSource("providePrimitiveTypes")
  public void equalsAndHashCodePrimitive(TypeName primitiveType) {
    assertEqualsHashCodeAndToString(primitiveType, primitiveType);
  }

  private static List<Arguments> providePrimitiveTypes() {
    return List.of(
      Arguments.of(TypeName.BOOLEAN),
      Arguments.of(TypeName.BYTE),
      Arguments.of(TypeName.CHAR),
      Arguments.of(TypeName.DOUBLE),
      Arguments.of(TypeName.FLOAT),
      Arguments.of(TypeName.INT),
      Arguments.of(TypeName.LONG),
      Arguments.of(TypeName.SHORT),
      Arguments.of(TypeName.VOID)
    );
  }

  @Test public void equalsAndHashCodeArrayTypeName() {
    assertEqualsHashCodeAndToString(ArrayTypeName.of(Object.class),
        ArrayTypeName.of(Object.class));
    assertEqualsHashCodeAndToString(TypeName.get(Object[].class),
        ArrayTypeName.of(Object.class));
  }

  @Test public void equalsAndHashCodeClassName() {
    assertEqualsHashCodeAndToString(ClassName.get(Object.class), ClassName.get(Object.class));
    assertEqualsHashCodeAndToString(TypeName.get(Object.class), ClassName.get(Object.class));
    assertEqualsHashCodeAndToString(ClassName.bestGuess("java.lang.Object"),
        ClassName.get(Object.class));
  }

  @Test public void equalsAndHashCodeParameterizedTypeName() {
    assertEqualsHashCodeAndToString(ParameterizedTypeName.get(Object.class),
        ParameterizedTypeName.get(Object.class));
    assertEqualsHashCodeAndToString(ParameterizedTypeName.get(Set.class, UUID.class),
        ParameterizedTypeName.get(Set.class, UUID.class));
    assertThat(ClassName.get(List.class))
      .isNotEqualTo(ParameterizedTypeName.get(List.class, String.class));
  }

  @Test public void equalsAndHashCodeTypeVariableName() {
    assertEqualsHashCodeAndToString(TypeVariableName.get(Object.class),
        TypeVariableName.get(Object.class));
    var typeVar1 = TypeVariableName.get("T", Comparator.class, Serializable.class);
    var typeVar2 = TypeVariableName.get("T", Comparator.class, Serializable.class);
    assertEqualsHashCodeAndToString(typeVar1, typeVar2);
  }

  @Test public void equalsAndHashCodeWildcardTypeName() {
    assertEqualsHashCodeAndToString(WildcardTypeName.subtypeOf(Object.class),
        WildcardTypeName.subtypeOf(Object.class));
    assertEqualsHashCodeAndToString(WildcardTypeName.subtypeOf(Serializable.class),
        WildcardTypeName.subtypeOf(Serializable.class));
    assertEqualsHashCodeAndToString(WildcardTypeName.supertypeOf(String.class),
        WildcardTypeName.supertypeOf(String.class));
  }

  @ParameterizedTest
  @MethodSource("providePrimitiveAndBoxedTypeChecks")
  public void primitiveAndBoxedTypeChecks(TypeName typeName, boolean expectedIsPrimitive, boolean expectedIsBoxedPrimitive) {
    assertThat(typeName.isPrimitive()).isEqualTo(expectedIsPrimitive);
    assertThat(typeName.isBoxedPrimitive()).isEqualTo(expectedIsBoxedPrimitive);
  }

  private static List<Arguments> providePrimitiveAndBoxedTypeChecks() {
    return List.of(
      Arguments.of(TypeName.INT, true, false),
      Arguments.of(ClassName.get("java.lang", "Integer"), false, true),
      Arguments.of(ClassName.get("java.lang", "String"), false, false),
      Arguments.of(TypeName.VOID, false, false),
      Arguments.of(ClassName.get("java.lang", "Void"), false, false),
      Arguments.of(ClassName.get("java.lang", "Integer").annotated(ANNOTATION_SPEC), false, true)
    );
  }

  @Test public void canBoxAnnotatedPrimitive() throws Exception {
    assertThat(TypeName.BOOLEAN.annotated(ANNOTATION_SPEC).box())
      .isEqualTo(ClassName.get("java.lang", "Boolean").annotated(ANNOTATION_SPEC));
  }

  @Test public void canUnboxAnnotatedPrimitive() throws Exception {
    assertThat(ClassName.get("java.lang", "Boolean").annotated(ANNOTATION_SPEC)
            .unbox())
      .isEqualTo(TypeName.BOOLEAN.annotated(ANNOTATION_SPEC));
  }

  private void assertEqualsHashCodeAndToString(TypeName a, TypeName b) {
    assertThat(a)
      .isNotNull()
      .isEqualTo(b)
      .hasSameHashCodeAs(b)
      .hasToString(b.toString());
  }
}
