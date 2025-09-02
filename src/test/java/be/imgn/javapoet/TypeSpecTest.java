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

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EventListener;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mockito;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(CompilationExtension.class)
public final class TypeSpecTest {
  private final String tacosPackage = "be.imgn.tacos";
  private static final String donutsPackage = "be.imgn.donuts";

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

  @Test public void basic() throws Exception {
    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(MethodSpec.methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .returns(String.class)
            .addCode("return $S;\n", "taco")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Override;
            import java.lang.String;

            class Taco {
              @Override
              public final String toString() {
                return "taco";
              }
            }
            """);
    assertThat(472949424).isEqualTo(taco.hashCode()); // update expected number if source changes
  }

  @Test public void interestingTypes() throws Exception {
    var listOfAny = ParameterizedTypeName.get(
        ClassName.get(List.class), WildcardTypeName.subtypeOf(Object.class));
    var listOfExtends = ParameterizedTypeName.get(
        ClassName.get(List.class), WildcardTypeName.subtypeOf(Serializable.class));
    var listOfSuper = ParameterizedTypeName.get(ClassName.get(List.class),
        WildcardTypeName.supertypeOf(String.class));
    var taco = TypeSpec.classBuilder("Taco")
        .addField(listOfAny, "extendsObject")
        .addField(listOfExtends, "extendsSerializable")
        .addField(listOfSuper, "superString")
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.io.Serializable;
            import java.lang.String;
            import java.util.List;

            class Taco {
              List<?> extendsObject;

              List<? extends Serializable> extendsSerializable;

              List<? super String> superString;
            }
            """);
  }

  @Test public void anonymousInnerClass() throws Exception {
    var foo = ClassName.get(tacosPackage, "Foo");
    var bar = ClassName.get(tacosPackage, "Bar");
    var thingThang = ClassName.get(tacosPackage, "Thing", "Thang");
    var thingThangOfFooBar = ParameterizedTypeName.get(thingThang, foo, bar);
    var thung = ClassName.get(tacosPackage, "Thung");
    var simpleThung = ClassName.get(tacosPackage, "SimpleThung");
    var thungOfSuperBar = ParameterizedTypeName.get(thung, WildcardTypeName.supertypeOf(bar));
    var thungOfSuperFoo = ParameterizedTypeName.get(thung, WildcardTypeName.supertypeOf(foo));
    var simpleThungOfBar = ParameterizedTypeName.get(simpleThung, bar);

    var thungParameter = ParameterSpec.builder(thungOfSuperFoo, "thung")
        .addModifiers(Modifier.FINAL)
        .build();
    var aSimpleThung = TypeSpec.anonymousClassBuilder(CodeBlock.of("$N", thungParameter))
        .superclass(simpleThungOfBar)
        .addMethod(MethodSpec.methodBuilder("doSomething")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(bar, "bar")
            .addCode("/* code snippets */\n")
            .build())
        .build();
    var aThingThang = TypeSpec.anonymousClassBuilder("")
        .superclass(thingThangOfFooBar)
        .addMethod(MethodSpec.methodBuilder("call")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(thungOfSuperBar)
            .addParameter(thungParameter)
            .addCode("return $L;\n", aSimpleThung)
            .build())
        .build();
    var taco = TypeSpec.classBuilder("Taco")
        .addField(FieldSpec.builder(thingThangOfFooBar, "NAME")
            .addModifiers(Modifier.STATIC, Modifier.FINAL, Modifier.FINAL)
            .initializer("$L", aThingThang)
            .build())
        .build();

    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Override;

            class Taco {
              static final Thing.Thang<Foo, Bar> NAME = new Thing.Thang<Foo, Bar>() {
                @Override
                public Thung<? super Bar> call(final Thung<? super Foo> thung) {
                  return new SimpleThung<Bar>(thung) {
                    @Override
                    public void doSomething(Bar bar) {
                      /* code snippets */
                    }
                  };
                }
              };
            }
            """);
  }

  @Test public void annotatedParameters() throws Exception {
    var service = TypeSpec.classBuilder("Foo")
        .addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(long.class, "id")
            .addParameter(ParameterSpec.builder(String.class, "one")
                .addAnnotation(ClassName.get(tacosPackage, "Ping"))
                .build())
            .addParameter(ParameterSpec.builder(String.class, "two")
                .addAnnotation(ClassName.get(tacosPackage, "Ping"))
                .build())
            .addParameter(ParameterSpec.builder(String.class, "three")
                .addAnnotation(AnnotationSpec.builder(ClassName.get(tacosPackage, "Pong"))
                    .addMember("value", "$S", "pong")
                    .build())
                .build())
            .addParameter(ParameterSpec.builder(String.class, "four")
                .addAnnotation(ClassName.get(tacosPackage, "Ping"))
                .build())
            .addCode("/* code snippets */\n")
            .build())
        .build();

    assertThat(toString(service)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.String;

            class Foo {
              public Foo(long id, @Ping String one, @Ping String two, @Pong("pong") String three,
                  @Ping String four) {
                /* code snippets */
              }
            }
            """);
  }

  /**
   * We had a bug where annotations were preventing us from doing the right thing when resolving
   * imports. https://github.com/square/javapoet/issues/422
   */
  @Test public void annotationsAndJavaLangTypes() throws Exception {
    var freeRange = ClassName.get("javax.annotation", "FreeRange");
    var taco = TypeSpec.classBuilder("EthicalTaco")
        .addField(ClassName.get(String.class)
            .annotated(AnnotationSpec.builder(freeRange).build()), "meat")
        .build();

    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.String;
            import javax.annotation.FreeRange;

            class EthicalTaco {
              @FreeRange String meat;
            }
            """);
  }

  @Test public void retrofitStyleInterface() throws Exception {
    var observable = ClassName.get(tacosPackage, "Observable");
    var fooBar = ClassName.get(tacosPackage, "FooBar");
    var thing = ClassName.get(tacosPackage, "Thing");
    var things = ClassName.get(tacosPackage, "Things");
    var map = ClassName.get("java.util", "Map");
    var string = ClassName.get("java.lang", "String");
    var headers = ClassName.get(tacosPackage, "Headers");
    var post = ClassName.get(tacosPackage, "POST");
    var body = ClassName.get(tacosPackage, "Body");
    var queryMap = ClassName.get(tacosPackage, "QueryMap");
    var header = ClassName.get(tacosPackage, "Header");
    var service = TypeSpec.interfaceBuilder("Service")
        .addMethod(MethodSpec.methodBuilder("fooBar")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(AnnotationSpec.builder(headers)
                .addMember("value", "$S", "Accept: application/json")
                .addMember("value", "$S", "User-Agent: foobar")
                .build())
            .addAnnotation(AnnotationSpec.builder(post)
                .addMember("value", "$S", "/foo/bar")
                .build())
            .returns(ParameterizedTypeName.get(observable, fooBar))
            .addParameter(ParameterSpec.builder(ParameterizedTypeName.get(things, thing), "things")
                .addAnnotation(body)
                .build())
            .addParameter(ParameterSpec.builder(
                ParameterizedTypeName.get(map, string, string), "query")
                .addAnnotation(AnnotationSpec.builder(queryMap)
                    .addMember("encodeValues", "false")
                    .build())
                .build())
            .addParameter(ParameterSpec.builder(string, "authorization")
                .addAnnotation(AnnotationSpec.builder(header)
                    .addMember("value", "$S", "Authorization")
                    .build())
                .build())
            .build())
        .build();

    assertThat(toString(service)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.String;
            import java.util.Map;

            interface Service {
              @Headers({
                  "Accept: application/json",
                  "User-Agent: foobar"
              })
              @POST("/foo/bar")
              Observable<FooBar> fooBar(@Body Things<Thing> things,
                  @QueryMap(encodeValues = false) Map<String, String> query,
                  @Header("Authorization") String authorization);
            }
            """);
  }

  @Test public void annotatedField() throws Exception {
    var taco = TypeSpec.classBuilder("Taco")
        .addField(FieldSpec.builder(String.class, "thing", Modifier.PRIVATE, Modifier.FINAL)
            .addAnnotation(AnnotationSpec.builder(ClassName.get(tacosPackage, "JsonAdapter"))
                .addMember("value", "$T.class", ClassName.get(tacosPackage, "Foo"))
                .build())
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.String;

            class Taco {
              @JsonAdapter(Foo.class)
              private final String thing;
            }
            """);
  }

  @Test public void annotatedClass() throws Exception {
    var someType = ClassName.get(tacosPackage, "SomeType");
    var taco = TypeSpec.classBuilder("Foo")
        .addAnnotation(AnnotationSpec.builder(ClassName.get(tacosPackage, "Something"))
            .addMember("hi", "$T.$N", someType, "FIELD")
            .addMember("hey", "$L", 12)
            .addMember("hello", "$S", "goodbye")
            .build())
        .addModifiers(Modifier.PUBLIC)
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            @Something(
                hi = SomeType.FIELD,
                hey = 12,
                hello = "goodbye"
            )
            public class Foo {
            }
            """);
  }

  @Test public void addAnnotationDisallowsNull() {
    assertThatThrownBy(() -> TypeSpec.classBuilder("Foo").addAnnotation((AnnotationSpec) null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("annotationSpec == null");
    assertThatThrownBy(() -> TypeSpec.classBuilder("Foo").addAnnotation((ClassName) null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("type == null");
    assertThatThrownBy(() -> TypeSpec.classBuilder("Foo").addAnnotation((Class<?>) null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("clazz == null");
  }

  @Test public void enumWithSubclassing() throws Exception {
    var roshambo = TypeSpec.enumBuilder("Roshambo")
        .addModifiers(Modifier.PUBLIC)
        .addEnumConstant("ROCK", TypeSpec.anonymousClassBuilder("")
            .addJavadoc("Avalanche!\n")
            .build())
        .addEnumConstant("PAPER", TypeSpec.anonymousClassBuilder("$S", "flat")
            .addMethod(MethodSpec.methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addCode("return $S;\n", "paper airplane!")
                .build())
            .build())
        .addEnumConstant("SCISSORS", TypeSpec.anonymousClassBuilder("$S", "peace sign")
            .build())
        .addField(String.class, "handPosition", Modifier.PRIVATE, Modifier.FINAL)
        .addMethod(MethodSpec.constructorBuilder()
            .addParameter(String.class, "handPosition")
            .addCode("this.handPosition = handPosition;\n")
            .build())
        .addMethod(MethodSpec.constructorBuilder()
            .addCode("this($S);\n", "fist")
            .build())
        .build();
    assertThat(toString(roshambo)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Override;
            import java.lang.String;

            public enum Roshambo {
              /**
               * Avalanche!
               */
              ROCK,

              PAPER("flat") {
                @Override
                public String toString() {
                  return "paper airplane!";
                }
              },

              SCISSORS("peace sign");

              private final String handPosition;

              Roshambo(String handPosition) {
                this.handPosition = handPosition;
              }

              Roshambo() {
                this("fist");
              }
            }
            """);
  }

  /** https://github.com/square/javapoet/issues/193 */
  @Test public void enumsMayDefineAbstractMethods() throws Exception {
    var roshambo = TypeSpec.enumBuilder("Tortilla")
        .addModifiers(Modifier.PUBLIC)
        .addEnumConstant("CORN", TypeSpec.anonymousClassBuilder("")
            .addMethod(MethodSpec.methodBuilder("fold")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .build())
            .build())
        .addMethod(MethodSpec.methodBuilder("fold")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .build())
        .build();
    assertThat(toString(roshambo)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Override;

            public enum Tortilla {
              CORN {
                @Override
                public void fold() {
                }
              };

              public abstract void fold();
            }
            """);
  }

  @Test public void noEnumConstants() throws Exception {
    var roshambo = TypeSpec.enumBuilder("Roshambo")
            .addField(String.class, "NO_ENUM", Modifier.STATIC)
            .build();
    assertThat(toString(roshambo)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.String;

            enum Roshambo {
              ;
              static String NO_ENUM;
            }
            """);
  }

  @Test public void onlyEnumsMayHaveEnumConstants() throws Exception {
    assertThatThrownBy(() ->
        TypeSpec.classBuilder("Roshambo")
          .addEnumConstant("ROCK")
          .build()
      )
      .isInstanceOf(IllegalStateException.class);
  }

  @Test public void enumWithMembersButNoConstructorCall() throws Exception {
    var roshambo = TypeSpec.enumBuilder("Roshambo")
        .addEnumConstant("SPOCK", TypeSpec.anonymousClassBuilder("")
            .addMethod(MethodSpec.methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addCode("return $S;\n", "west side")
                .build())
            .build())
        .build();
    assertThat(toString(roshambo)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Override;
            import java.lang.String;

            enum Roshambo {
              SPOCK {
                @Override
                public String toString() {
                  return "west side";
                }
              }
            }
            """);
  }

  /** https://github.com/square/javapoet/issues/253 */
  @Test public void enumWithAnnotatedValues() throws Exception {
    var roshambo = TypeSpec.enumBuilder("Roshambo")
        .addModifiers(Modifier.PUBLIC)
        .addEnumConstant("ROCK", TypeSpec.anonymousClassBuilder("")
            .addAnnotation(Deprecated.class)
            .build())
        .addEnumConstant("PAPER")
        .addEnumConstant("SCISSORS")
        .build();
    assertThat(toString(roshambo)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Deprecated;

            public enum Roshambo {
              @Deprecated
              ROCK,

              PAPER,

              SCISSORS
            }
            """);
  }

  @Test public void methodThrows() throws Exception {
    var taco = TypeSpec.classBuilder("Taco")
        .addModifiers(Modifier.ABSTRACT)
        .addMethod(MethodSpec.methodBuilder("throwOne")
            .addException(IOException.class)
            .build())
        .addMethod(MethodSpec.methodBuilder("throwTwo")
            .addException(IOException.class)
            .addException(ClassName.get(tacosPackage, "SourCreamException"))
            .build())
        .addMethod(MethodSpec.methodBuilder("abstractThrow")
            .addModifiers(Modifier.ABSTRACT)
            .addException(IOException.class)
            .build())
        .addMethod(MethodSpec.methodBuilder("nativeThrow")
            .addModifiers(Modifier.NATIVE)
            .addException(IOException.class)
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.io.IOException;

            abstract class Taco {
              void throwOne() throws IOException {
              }

              void throwTwo() throws IOException, SourCreamException {
              }

              abstract void abstractThrow() throws IOException;

              native void nativeThrow() throws IOException;
            }
            """);
  }

  @Test public void typeVariables() throws Exception {
    var t = TypeVariableName.get("T");
    var p = TypeVariableName.get("P", Number.class);
    var location = ClassName.get(tacosPackage, "Location");
    var typeSpec = TypeSpec.classBuilder("Location")
        .addTypeVariable(t)
        .addTypeVariable(p)
        .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Comparable.class), p))
        .addField(t, "label")
        .addField(p, "x")
        .addField(p, "y")
        .addMethod(MethodSpec.methodBuilder("compareTo")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(int.class)
            .addParameter(p, "p")
            .addCode("return 0;\n")
            .build())
        .addMethod(MethodSpec.methodBuilder("of")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addTypeVariable(t)
            .addTypeVariable(p)
            .returns(ParameterizedTypeName.get(location, t, p))
            .addParameter(t, "label")
            .addParameter(p, "x")
            .addParameter(p, "y")
            .addCode("throw new $T($S);\n", UnsupportedOperationException.class, "TODO")
            .build())
        .build();
    assertThat(toString(typeSpec)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Comparable;
            import java.lang.Number;
            import java.lang.Override;
            import java.lang.UnsupportedOperationException;

            class Location<T, P extends Number> implements Comparable<P> {
              T label;

              P x;

              P y;

              @Override
              public int compareTo(P p) {
                return 0;
              }

              public static <T, P extends Number> Location<T, P> of(T label, P x, P y) {
                throw new UnsupportedOperationException("TODO");
              }
            }
            """);
  }

  @Test public void typeVariableWithBounds() {
    var a = AnnotationSpec.builder(ClassName.get("be.imgn.tacos", "A")).build();
    var p = TypeVariableName.get("P", Number.class);
    var q = (TypeVariableName) TypeVariableName.get("Q", Number.class).annotated(a);
    var typeSpec = TypeSpec.classBuilder("Location")
        .addTypeVariable(p.withBounds(Comparable.class))
        .addTypeVariable(q.withBounds(Comparable.class))
        .addField(p, "x")
        .addField(q, "y")
        .build();
    assertThat(toString(typeSpec)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Comparable;
            import java.lang.Number;

            class Location<P extends Number & Comparable, @A Q extends Number & Comparable> {
              P x;

              @A Q y;
            }
            """);
  }

  @Test public void classImplementsExtends() throws Exception {
    var taco = ClassName.get(tacosPackage, "Taco");
    var food = ClassName.get("be.imgn.tacos", "Food");
    var typeSpec = TypeSpec.classBuilder("Taco")
        .addModifiers(Modifier.ABSTRACT)
        .superclass(ParameterizedTypeName.get(ClassName.get(AbstractSet.class), food))
        .addSuperinterface(Serializable.class)
        .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Comparable.class), taco))
        .build();
    assertThat(toString(typeSpec)).isEqualTo("""
            package be.imgn.tacos;

            import java.io.Serializable;
            import java.lang.Comparable;
            import java.util.AbstractSet;

            abstract class Taco extends AbstractSet<Food> \
            implements Serializable, Comparable<Taco> {
            }
            """);
  }

  @Test public void classImplementsNestedClass() throws Exception {
    var outer = ClassName.get(tacosPackage, "Outer");
    var inner = outer.nestedClass("Inner");
    var callable = ClassName.get(Callable.class);
    var typeSpec = TypeSpec.classBuilder("Outer")
        .superclass(ParameterizedTypeName.get(callable,
            inner))
        .addType(TypeSpec.classBuilder("Inner")
            .addModifiers(Modifier.STATIC)
            .build())
        .build();

    assertThat(toString(typeSpec)).isEqualTo("""
            package be.imgn.tacos;

            import java.util.concurrent.Callable;

            class Outer extends Callable<Outer.Inner> {
              static class Inner {
              }
            }
            """);
  }

  @Test public void enumImplements() throws Exception {
    var typeSpec = TypeSpec.enumBuilder("Food")
        .addSuperinterface(Serializable.class)
        .addSuperinterface(Cloneable.class)
        .addEnumConstant("LEAN_GROUND_BEEF")
        .addEnumConstant("SHREDDED_CHEESE")
        .build();
    assertThat(toString(typeSpec)).isEqualTo("""
            package be.imgn.tacos;

            import java.io.Serializable;
            import java.lang.Cloneable;

            enum Food implements Serializable, Cloneable {
              LEAN_GROUND_BEEF,

              SHREDDED_CHEESE
            }
            """);
  }

  @Test public void interfaceExtends() throws Exception {
    var taco = ClassName.get(tacosPackage, "Taco");
    var typeSpec = TypeSpec.interfaceBuilder("Taco")
        .addSuperinterface(Serializable.class)
        .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Comparable.class), taco))
        .build();
    assertThat(toString(typeSpec)).isEqualTo("""
            package be.imgn.tacos;

            import java.io.Serializable;
            import java.lang.Comparable;

            interface Taco extends Serializable, Comparable<Taco> {
            }
            """);
  }

  @Test public void nestedClasses() throws Exception {
    var taco = ClassName.get(tacosPackage, "Combo", "Taco");
    var topping = ClassName.get(tacosPackage, "Combo", "Taco", "Topping");
    var chips = ClassName.get(tacosPackage, "Combo", "Chips");
    var sauce = ClassName.get(tacosPackage, "Combo", "Sauce");
    var typeSpec = TypeSpec.classBuilder("Combo")
        .addField(taco, "taco")
        .addField(chips, "chips")
        .addType(TypeSpec.classBuilder(taco.simpleName())
            .addModifiers(Modifier.STATIC)
            .addField(ParameterizedTypeName.get(ClassName.get(List.class), topping), "toppings")
            .addField(sauce, "sauce")
            .addType(TypeSpec.enumBuilder(topping.simpleName())
                .addEnumConstant("SHREDDED_CHEESE")
                .addEnumConstant("LEAN_GROUND_BEEF")
                .build())
            .build())
        .addType(TypeSpec.classBuilder(chips.simpleName())
            .addModifiers(Modifier.STATIC)
            .addField(topping, "topping")
            .addField(sauce, "dippingSauce")
            .build())
        .addType(TypeSpec.enumBuilder(sauce.simpleName())
            .addEnumConstant("SOUR_CREAM")
            .addEnumConstant("SALSA")
            .addEnumConstant("QUESO")
            .addEnumConstant("MILD")
            .addEnumConstant("FIRE")
            .build())
        .build();

    assertThat(toString(typeSpec)).isEqualTo("""
            package be.imgn.tacos;

            import java.util.List;

            class Combo {
              Taco taco;

              Chips chips;

              static class Taco {
                List<Topping> toppings;

                Sauce sauce;

                enum Topping {
                  SHREDDED_CHEESE,

                  LEAN_GROUND_BEEF
                }
              }

              static class Chips {
                Taco.Topping topping;

                Sauce dippingSauce;
              }

              enum Sauce {
                SOUR_CREAM,

                SALSA,

                QUESO,

                MILD,

                FIRE
              }
            }
            """);
  }

  @Test public void annotation() throws Exception {
    var annotation = TypeSpec.annotationBuilder("MyAnnotation")
        .addModifiers(Modifier.PUBLIC)
        .addMethod(MethodSpec.methodBuilder("test")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .defaultValue("$L", 0)
            .returns(int.class)
            .build())
        .build();

    assertThat(toString(annotation)).isEqualTo("""
            package be.imgn.tacos;

            public @interface MyAnnotation {
              int test() default 0;
            }
            """
    );
  }

  @Test public void innerAnnotationInAnnotationDeclaration() throws Exception {
    var bar = TypeSpec.annotationBuilder("Bar")
        .addMethod(MethodSpec.methodBuilder("value")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .defaultValue("@$T", Deprecated.class)
            .returns(Deprecated.class)
            .build())
        .build();

    assertThat(toString(bar)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Deprecated;

            @interface Bar {
              Deprecated value() default @Deprecated;
            }
            """
    );
  }

  @Test public void annotationWithFields() {
    var field = FieldSpec.builder(int.class, "FOO")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .initializer("$L", 101)
        .build();

    var anno = TypeSpec.annotationBuilder("Anno")
        .addField(field)
        .build();

    assertThat(toString(anno)).isEqualTo("""
            package be.imgn.tacos;

            @interface Anno {
              int FOO = 101;
            }
            """
    );
  }

  @Test
  public void classCannotHaveDefaultValueForMethod() throws Exception {
    assertThatThrownBy(() ->
      TypeSpec.classBuilder("Tacos")
          .addMethod(MethodSpec.methodBuilder("test")
              .addModifiers(Modifier.PUBLIC)
              .defaultValue("0")
              .returns(int.class)
              .build())
          .build()
    )
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void classCannotHaveDefaultMethods() throws Exception {
    assertThatThrownBy(() ->
      TypeSpec.classBuilder("Tacos")
        .addMethod(MethodSpec.methodBuilder("test")
          .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
          .returns(int.class)
          .addCode(CodeBlock.builder().addStatement("return 0").build())
          .build())
        .build()
    )
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void interfaceStaticMethods() throws Exception {
    var bar = TypeSpec.interfaceBuilder("Tacos")
        .addMethod(MethodSpec.methodBuilder("test")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(int.class)
            .addCode(CodeBlock.builder().addStatement("return 0").build())
            .build())
        .build();

    assertThat(toString(bar)).isEqualTo("""
            package be.imgn.tacos;

            interface Tacos {
              static int test() {
                return 0;
              }
            }
            """
    );
  }

  @Test
  public void interfaceDefaultMethods() throws Exception {
    var bar = TypeSpec.interfaceBuilder("Tacos")
        .addMethod(MethodSpec.methodBuilder("test")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(int.class)
            .addCode(CodeBlock.builder().addStatement("return 0").build())
            .build())
        .build();

    assertThat(toString(bar)).isEqualTo("""
            package be.imgn.tacos;

            interface Tacos {
              default int test() {
                return 0;
              }
            }
            """
    );
  }

  @Test
  public void invalidInterfacePrivateMethods() {
    assertThatThrownBy(() ->
      TypeSpec.interfaceBuilder("Tacos")
        .addMethod(MethodSpec.methodBuilder("test")
          .addModifiers(Modifier.PRIVATE, Modifier.DEFAULT)
          .returns(int.class)
          .addCode(CodeBlock.builder().addStatement("return 0").build())
          .build())
        .build()
      )
      .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() ->
      TypeSpec.interfaceBuilder("Tacos")
        .addMethod(MethodSpec.methodBuilder("test")
          .addModifiers(Modifier.PRIVATE, Modifier.ABSTRACT)
          .returns(int.class)
          .build())
        .build()
    )
      .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() ->
        TypeSpec.interfaceBuilder("Tacos")
          .addMethod(MethodSpec.methodBuilder("test")
            .addModifiers(Modifier.PRIVATE, Modifier.PUBLIC)
            .returns(int.class)
            .addCode(CodeBlock.builder().addStatement("return 0").build())
            .build())
          .build()
    )
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void interfacePrivateMethods() {
    var bar = TypeSpec.interfaceBuilder("Tacos")
        .addMethod(MethodSpec.methodBuilder("test")
            .addModifiers(Modifier.PRIVATE)
            .returns(int.class)
            .addCode(CodeBlock.builder().addStatement("return 0").build())
            .build())
        .build();

    assertThat(toString(bar)).isEqualTo("""
            package be.imgn.tacos;

            interface Tacos {
              private int test() {
                return 0;
              }
            }
            """
    );

    bar = TypeSpec.interfaceBuilder("Tacos")
        .addMethod(MethodSpec.methodBuilder("test")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(int.class)
            .addCode(CodeBlock.builder().addStatement("return 0").build())
            .build())
        .build();

    assertThat(toString(bar)).isEqualTo("""
            package be.imgn.tacos;

            interface Tacos {
              private static int test() {
                return 0;
              }
            }
            """
    );
  }

  @Test public void referencedAndDeclaredSimpleNamesConflict() throws Exception {
    var internalTop = FieldSpec.builder(
        ClassName.get(tacosPackage, "Top"), "internalTop").build();
    var internalBottom = FieldSpec.builder(
        ClassName.get(tacosPackage, "Top", "Middle", "Bottom"), "internalBottom").build();
    var externalTop = FieldSpec.builder(
        ClassName.get(donutsPackage, "Top"), "externalTop").build();
    var externalBottom = FieldSpec.builder(
        ClassName.get(donutsPackage, "Bottom"), "externalBottom").build();
    var top = TypeSpec.classBuilder("Top")
        .addField(internalTop)
        .addField(internalBottom)
        .addField(externalTop)
        .addField(externalBottom)
        .addType(TypeSpec.classBuilder("Middle")
            .addField(internalTop)
            .addField(internalBottom)
            .addField(externalTop)
            .addField(externalBottom)
            .addType(TypeSpec.classBuilder("Bottom")
                .addField(internalTop)
                .addField(internalBottom)
                .addField(externalTop)
                .addField(externalBottom)
                .build())
            .build())
        .build();
    assertThat(toString(top)).isEqualTo("""
            package be.imgn.tacos;

            import be.imgn.donuts.Bottom;

            class Top {
              Top internalTop;

              Middle.Bottom internalBottom;

              be.imgn.donuts.Top externalTop;

              Bottom externalBottom;

              class Middle {
                Top internalTop;

                Bottom internalBottom;

                be.imgn.donuts.Top externalTop;

                be.imgn.donuts.Bottom externalBottom;

                class Bottom {
                  Top internalTop;

                  Bottom internalBottom;

                  be.imgn.donuts.Top externalTop;

                  be.imgn.donuts.Bottom externalBottom;
                }
              }
            }
            """);
  }

  @Test public void simpleNamesConflictInThisAndOtherPackage() throws Exception {
    var internalOther = FieldSpec.builder(
        ClassName.get(tacosPackage, "Other"), "internalOther").build();
    var externalOther = FieldSpec.builder(
        ClassName.get(donutsPackage, "Other"), "externalOther").build();
    var gen = TypeSpec.classBuilder("Gen")
        .addField(internalOther)
        .addField(externalOther)
        .build();
    assertThat(toString(gen)).isEqualTo("""
            package be.imgn.tacos;

            class Gen {
              Other internalOther;

              be.imgn.donuts.Other externalOther;
            }
            """);
  }

  @Test public void simpleNameConflictsWithTypeVariable() {
    var inPackage = ClassName.get("be.imgn.tacos", "InPackage");
    var otherType = ClassName.get("com.other", "OtherType");
    var methodInPackage = ClassName.get("be.imgn.tacos", "MethodInPackage");
    var methodOtherType = ClassName.get("com.other", "MethodOtherType");
    var gen = TypeSpec.classBuilder("Gen")
        .addTypeVariable(TypeVariableName.get("InPackage"))
        .addTypeVariable(TypeVariableName.get("OtherType"))
        .addField(FieldSpec.builder(inPackage, "inPackage").build())
        .addField(FieldSpec.builder(otherType, "otherType").build())
        .addMethod(MethodSpec.methodBuilder("withTypeVariables")
            .addTypeVariable(TypeVariableName.get("MethodInPackage"))
            .addTypeVariable(TypeVariableName.get("MethodOtherType"))
            .addStatement("$T inPackage = null", methodInPackage)
            .addStatement("$T otherType = null", methodOtherType)
            .build())
        .addMethod(MethodSpec.methodBuilder("withoutTypeVariables")
            .addStatement("$T inPackage = null", methodInPackage)
            .addStatement("$T otherType = null", methodOtherType)
            .build())
        .addMethod(MethodSpec.methodBuilder("againWithTypeVariables")
            .addTypeVariable(TypeVariableName.get("MethodInPackage"))
            .addTypeVariable(TypeVariableName.get("MethodOtherType"))
            .addStatement("$T inPackage = null", methodInPackage)
            .addStatement("$T otherType = null", methodOtherType)
            .build())
        // https://github.com/square/javapoet/pull/657#discussion_r205514292
        .addMethod(MethodSpec.methodBuilder("masksEnclosingTypeVariable")
            .addTypeVariable(TypeVariableName.get("InPackage"))
            .build())
        .addMethod(MethodSpec.methodBuilder("hasSimpleNameThatWasPreviouslyMasked")
            .addStatement("$T inPackage = null", inPackage)
            .build())
        .build();
    assertThat(toString(gen)).isEqualTo("""
            package be.imgn.tacos;

            import com.other.MethodOtherType;

            class Gen<InPackage, OtherType> {
              be.imgn.tacos.InPackage inPackage;

              com.other.OtherType otherType;

              <MethodInPackage, MethodOtherType> void withTypeVariables() {
                be.imgn.tacos.MethodInPackage inPackage = null;
                com.other.MethodOtherType otherType = null;
              }

              void withoutTypeVariables() {
                MethodInPackage inPackage = null;
                MethodOtherType otherType = null;
              }

              <MethodInPackage, MethodOtherType> void againWithTypeVariables() {
                be.imgn.tacos.MethodInPackage inPackage = null;
                com.other.MethodOtherType otherType = null;
              }

              <InPackage> void masksEnclosingTypeVariable() {
              }

              void hasSimpleNameThatWasPreviouslyMasked() {
                be.imgn.tacos.InPackage inPackage = null;
              }
            }
            """);
  }

  @Test public void originatingElementsIncludesThoseOfNestedTypes() {
    var outerElement = Mockito.mock(Element.class);
    var innerElement = Mockito.mock(Element.class);
    var outer = TypeSpec.classBuilder("Outer")
        .addOriginatingElement(outerElement)
        .addType(TypeSpec.classBuilder("Inner")
            .addOriginatingElement(innerElement)
            .build())
        .build();
    assertThat(outer.originatingElements).containsExactly(outerElement, innerElement);
  }

  @Test public void intersectionType() {
    var typeVariable = TypeVariableName.get("T", Comparator.class, Serializable.class);
    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(MethodSpec.methodBuilder("getComparator")
            .addTypeVariable(typeVariable)
            .returns(typeVariable)
            .addCode("return null;\n")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.io.Serializable;
            import java.util.Comparator;

            class Taco {
              <T extends Comparator & Serializable> T getComparator() {
                return null;
              }
            }
            """);
  }

  @Test public void arrayType() {
    var taco = TypeSpec.classBuilder("Taco")
        .addField(int[].class, "ints")
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            class Taco {
              int[] ints;
            }
            """);
  }

  @Test public void javadoc() {
    var taco = TypeSpec.classBuilder("Taco")
        .addJavadoc("A hard or soft tortilla, loosely folded and filled with whatever {@link \n")
        .addJavadoc("{@link $T random} tex-mex stuff we could find in the pantry\n", Random.class)
        .addJavadoc(CodeBlock.of("and some {@link $T} cheese.\n", String.class))
        .addField(FieldSpec.builder(boolean.class, "soft")
            .addJavadoc("True for a soft flour tortilla; false for a crunchy corn tortilla.\n")
            .build())
        .addMethod(MethodSpec.methodBuilder("refold")
            .addJavadoc("Folds the back of this taco to reduce sauce leakage.\n"
                + "\n"
                + "<p>For {@link $T#KOREAN}, the front may also be folded.\n", Locale.class)
            .addParameter(Locale.class, "locale")
            .build())
        .build();
    // Mentioning a type in Javadoc will not cause an import to be added (java.util.Random here),
    // but the short name will be used if it's already imported (java.util.Locale here).
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.util.Locale;

            /**
             * A hard or soft tortilla, loosely folded and filled with whatever {@link\s
             * {@link java.util.Random random} tex-mex stuff we could find in the pantry
             * and some {@link java.lang.String} cheese.
             */
            class Taco {
              /**
               * True for a soft flour tortilla; false for a crunchy corn tortilla.
               */
              boolean soft;

              /**
               * Folds the back of this taco to reduce sauce leakage.
               *
               * <p>For {@link Locale#KOREAN}, the front may also be folded.
               */
              void refold(Locale locale) {
              }
            }
            """);
  }

  @Test public void annotationsInAnnotations() throws Exception {
    var beef = ClassName.get(tacosPackage, "Beef");
    var chicken = ClassName.get(tacosPackage, "Chicken");
    var option = ClassName.get(tacosPackage, "Option");
    var mealDeal = ClassName.get(tacosPackage, "MealDeal");
    var menu = TypeSpec.classBuilder("Menu")
        .addAnnotation(AnnotationSpec.builder(mealDeal)
            .addMember("price", "$L", 500)
            .addMember("options", "$L", AnnotationSpec.builder(option)
                .addMember("name", "$S", "taco")
                .addMember("meat", "$T.class", beef)
                .build())
            .addMember("options", "$L", AnnotationSpec.builder(option)
                .addMember("name", "$S", "quesadilla")
                .addMember("meat", "$T.class", chicken)
                .build())
            .build())
        .build();
    assertThat(toString(menu)).isEqualTo("""
            package be.imgn.tacos;

            @MealDeal(
                price = 500,
                options = {
                    @Option(name = "taco", meat = Beef.class),
                    @Option(name = "quesadilla", meat = Chicken.class)
                }
            )
            class Menu {
            }
            """);
  }

  @Test public void varargs() throws Exception {
    var taqueria = TypeSpec.classBuilder("Taqueria")
        .addMethod(MethodSpec.methodBuilder("prepare")
            .addParameter(int.class, "workers")
            .addParameter(Runnable[].class, "jobs")
            .varargs()
            .build())
        .build();
    assertThat(toString(taqueria)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Runnable;

            class Taqueria {
              void prepare(int workers, Runnable... jobs) {
              }
            }
            """);
  }

  @Test public void codeBlocks() throws Exception {
    var ifBlock = CodeBlock.builder()
        .beginControlFlow("if (!a.equals(b))")
        .addStatement("return i")
        .endControlFlow()
        .build();
    var methodBody = CodeBlock.builder()
        .addStatement("$T size = $T.min(listA.size(), listB.size())", int.class, Math.class)
        .beginControlFlow("for ($T i = 0; i < size; i++)", int.class)
        .addStatement("$T $N = $N.get(i)", String.class, "a", "listA")
        .addStatement("$T $N = $N.get(i)", String.class, "b", "listB")
        .add("$L", ifBlock)
        .endControlFlow()
        .addStatement("return size")
        .build();
    var fieldBlock = CodeBlock.builder()
        .add("$>$>")
        .add("\n$T.<$T, $T>builder()$>$>", ImmutableMap.class, String.class, String.class)
        .add("\n.add($S, $S)", '\'', "&#39;")
        .add("\n.add($S, $S)", '&', "&amp;")
        .add("\n.add($S, $S)", '<', "&lt;")
        .add("\n.add($S, $S)", '>', "&gt;")
        .add("\n.build()$<$<")
        .add("$<$<")
        .build();
    var escapeHtml = FieldSpec.builder(ParameterizedTypeName.get(
        Map.class, String.class, String.class), "ESCAPE_HTML")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .initializer(fieldBlock)
        .build();
    var util = TypeSpec.classBuilder("Util")
        .addField(escapeHtml)
        .addMethod(MethodSpec.methodBuilder("commonPrefixLength")
            .returns(int.class)
            .addParameter(ParameterizedTypeName.get(List.class, String.class), "listA")
            .addParameter(ParameterizedTypeName.get(List.class, String.class), "listB")
            .addCode(methodBody)
            .build())
        .build();
    assertThat(toString(util)).isEqualTo("""
            package be.imgn.tacos;

            import com.google.common.collect.ImmutableMap;
            import java.lang.Math;
            import java.lang.String;
            import java.util.List;
            import java.util.Map;

            class Util {
              private static final Map<String, String> ESCAPE_HTML =\s
                  ImmutableMap.<String, String>builder()
                      .add("\'", "&#39;")
                      .add("&", "&amp;")
                      .add("<", "&lt;")
                      .add(">", "&gt;")
                      .build();

              int commonPrefixLength(List<String> listA, List<String> listB) {
                int size = Math.min(listA.size(), listB.size());
                for (int i = 0; i < size; i++) {
                  String a = listA.get(i);
                  String b = listB.get(i);
                  if (!a.equals(b)) {
                    return i;
                  }
                }
                return size;
              }
            }
            """);
  }

  @Test public void indexedElseIf() throws Exception {
    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(MethodSpec.methodBuilder("choices")
            .beginControlFlow("if ($1L != null || $1L == $2L)", "taco", "otherTaco")
            .addStatement("$T.out.println($S)", System.class, "only one taco? NOO!")
            .nextControlFlow("else if ($1L.$3L && $2L.$3L)", "taco", "otherTaco", "isSupreme()")
            .addStatement("$T.out.println($S)", System.class, "taco heaven")
            .endControlFlow()
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.System;

            class Taco {
              void choices() {
                if (taco != null || taco == otherTaco) {
                  System.out.println("only one taco? NOO!");
                } else if (taco.isSupreme() && otherTaco.isSupreme()) {
                  System.out.println("taco heaven");
                }
              }
            }
            """);
  }

  @Test public void elseIf() throws Exception {
    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(MethodSpec.methodBuilder("choices")
            .beginControlFlow("if (5 < 4) ")
            .addStatement("$T.out.println($S)", System.class, "wat")
            .nextControlFlow("else if (5 < 6)")
            .addStatement("$T.out.println($S)", System.class, "hello")
            .endControlFlow()
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.System;

            class Taco {
              void choices() {
                if (5 < 4)  {
                  System.out.println("wat");
                } else if (5 < 6) {
                  System.out.println("hello");
                }
              }
            }
            """);
  }

  @Test public void doWhile() throws Exception {
    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(MethodSpec.methodBuilder("loopForever")
            .beginControlFlow("do")
            .addStatement("$T.out.println($S)", System.class, "hello")
            .endControlFlow("while (5 < 6)")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.System;

            class Taco {
              void loopForever() {
                do {
                  System.out.println("hello");
                } while (5 < 6);
              }
            }
            """);
  }

  @Test public void inlineIndent() throws Exception {
    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(MethodSpec.methodBuilder("inlineIndent")
            .addCode("if (3 < 4) {\n$>$T.out.println($S);\n$<}\n", System.class, "hello")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.System;

            class Taco {
              void inlineIndent() {
                if (3 < 4) {
                  System.out.println("hello");
                }
              }
            }
            """);
  }

  @Test public void defaultModifiersForInterfaceMembers() throws Exception {
    var taco = TypeSpec.interfaceBuilder("Taco")
        .addField(FieldSpec.builder(String.class, "SHELL")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", "crunchy corn")
            .build())
        .addMethod(MethodSpec.methodBuilder("fold")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .build())
        .addType(TypeSpec.classBuilder("Topping")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.String;

            interface Taco {
              String SHELL = "crunchy corn";

              void fold();

              class Topping {
              }
            }
            """);
  }

  @Test public void defaultModifiersForMemberInterfacesAndEnums() throws Exception {
    var taco = TypeSpec.classBuilder("Taco")
        .addType(TypeSpec.classBuilder("Meat")
            .addModifiers(Modifier.STATIC)
            .build())
        .addType(TypeSpec.interfaceBuilder("Tortilla")
            .addModifiers(Modifier.STATIC)
            .build())
        .addType(TypeSpec.enumBuilder("Topping")
            .addModifiers(Modifier.STATIC)
            .addEnumConstant("SALSA")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            class Taco {
              static class Meat {
              }

              interface Tortilla {
              }

              enum Topping {
                SALSA
              }
            }
            """);
  }

  @Test public void membersOrdering() throws Exception {
    // Hand out names in reverse-alphabetical order to defend against unexpected sorting.
    var taco = TypeSpec.classBuilder("Members")
        .addType(TypeSpec.classBuilder("Z").build())
        .addType(TypeSpec.classBuilder("Y").build())
        .addField(String.class, "X", Modifier.STATIC)
        .addField(String.class, "W")
        .addField(String.class, "V", Modifier.STATIC)
        .addField(String.class, "U")
        .addMethod(MethodSpec.methodBuilder("T").addModifiers(Modifier.STATIC).build())
        .addMethod(MethodSpec.methodBuilder("S").build())
        .addMethod(MethodSpec.methodBuilder("R").addModifiers(Modifier.STATIC).build())
        .addMethod(MethodSpec.methodBuilder("Q").build())
        .addMethod(MethodSpec.constructorBuilder().addParameter(int.class, "p").build())
        .addMethod(MethodSpec.constructorBuilder().addParameter(long.class, "o").build())
        .build();
    // Static fields, instance fields, constructors, methods, classes.
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.String;

            class Members {
              static String X;

              static String V;

              String W;

              String U;

              Members(int p) {
              }

              Members(long o) {
              }

              static void T() {
              }

              void S() {
              }

              static void R() {
              }

              void Q() {
              }

              class Z {
              }

              class Y {
              }
            }
            """);
  }

  @Test public void nativeMethods() throws Exception {
    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(MethodSpec.methodBuilder("nativeInt")
            .addModifiers(Modifier.NATIVE)
            .returns(int.class)
            .build())
        // GWT JSNI
        .addMethod(MethodSpec.methodBuilder("alert")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.NATIVE)
            .addParameter(String.class, "msg")
            .addCode(CodeBlock.builder()
                .add(" /*-{\n")
                .indent()
                .addStatement("$$wnd.alert(msg)")
                .unindent()
                .add("}-*/")
                .build())
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.String;

            class Taco {
              native int nativeInt();

              public static native void alert(String msg) /*-{
                $wnd.alert(msg);
              }-*/;
            }
            """);
  }

  @Test public void nullStringLiteral() throws Exception {
    var taco = TypeSpec.classBuilder("Taco")
        .addField(FieldSpec.builder(String.class, "NULL")
            .initializer("$S", (Object) null)
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.String;

            class Taco {
              String NULL = null;
            }
            """);
  }

  @Test public void annotationToString() throws Exception {
    var annotation = AnnotationSpec.builder(SuppressWarnings.class)
        .addMember("value", "$S", "unused")
        .build();
    assertThat(annotation)
      .hasToString("@java.lang.SuppressWarnings(\"unused\")");
  }

  @Test public void codeBlockToString() throws Exception {
    var codeBlock = CodeBlock.builder()
        .addStatement("$T $N = $S.substring(0, 3)", String.class, "s", "taco")
        .build();
    assertThat(codeBlock)
      .hasToString("java.lang.String s = \"taco\".substring(0, 3);\n");
  }

  @Test public void codeBlockAddStatementOfCodeBlockToString() throws Exception {
    var contents = CodeBlock.of("$T $N = $S.substring(0, 3)", String.class, "s", "taco");
    var statement = CodeBlock.builder().addStatement(contents).build();
    assertThat(statement)
      .hasToString("java.lang.String s = \"taco\".substring(0, 3);\n");
  }

  @Test public void fieldToString() throws Exception {
    var field = FieldSpec.builder(String.class, "s", Modifier.FINAL)
        .initializer("$S.substring(0, 3)", "taco")
        .build();
    assertThat(field.toString())
        .isEqualTo("final java.lang.String s = \"taco\".substring(0, 3);\n");
  }

  @Test public void methodToString() throws Exception {
    var method = MethodSpec.methodBuilder("toString")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(String.class)
        .addStatement("return $S", "taco")
        .build();
    assertThat(method)
      .hasToString("""
            @java.lang.Override
            public java.lang.String toString() {
              return "taco";
            }
            """);
  }

  @Test public void constructorToString() throws Exception {
    var constructor = MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ClassName.get(tacosPackage, "Taco"), "taco")
        .addStatement("this.$N = $N", "taco", "taco")
        .build();
    assertThat(constructor)
      .hasToString("""
            public Constructor(be.imgn.tacos.Taco taco) {
              this.taco = taco;
            }
            """);
  }

  @Test public void parameterToString() throws Exception {
    var parameter = ParameterSpec.builder(ClassName.get(tacosPackage, "Taco"), "taco")
        .addModifiers(Modifier.FINAL)
        .addAnnotation(ClassName.get("javax.annotation", "Nullable"))
        .build();
    assertThat(parameter.toString())
        .isEqualTo("@javax.annotation.Nullable final be.imgn.tacos.Taco taco");
  }

  @Test public void classToString() throws Exception {
    var type = TypeSpec.classBuilder("Taco")
        .build();
    assertThat(type)
      .hasToString("""
        class Taco {
        }
        """);
  }

  @Test public void anonymousClassToString() throws Exception {
    var type = TypeSpec.anonymousClassBuilder("")
        .addSuperinterface(Runnable.class)
        .addMethod(MethodSpec.methodBuilder("run")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .build())
        .build();
    assertThat(type)
      .hasToString("""
            new java.lang.Runnable() {
              @java.lang.Override
              public void run() {
              }
            }""");
  }

  @Test public void interfaceClassToString() throws Exception {
    var type = TypeSpec.interfaceBuilder("Taco")
        .build();
    assertThat(type)
      .hasToString("""
            interface Taco {
            }
            """);
  }

  @Test public void annotationDeclarationToString() throws Exception {
    var type = TypeSpec.annotationBuilder("Taco")
        .build();
    assertThat(type)
      .hasToString("""
            @interface Taco {
            }
            """);
  }

  private String toString(TypeSpec typeSpec) {
    return JavaFile.builder(tacosPackage, typeSpec).build().toString();
  }

  @Test public void multilineStatement() throws Exception {
    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(MethodSpec.methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return $S\n+ $S\n+ $S\n+ $S\n+ $S",
                "Taco(", "beef,", "lettuce,", "cheese", ")")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Override;
            import java.lang.String;

            class Taco {
              @Override
              public String toString() {
                return "Taco("
                    + "beef,"
                    + "lettuce,"
                    + "cheese"
                    + ")";
              }
            }
            """);
  }

  @Test public void multilineStatementWithAnonymousClass() throws Exception {
    var stringComparator = ParameterizedTypeName.get(Comparator.class, String.class);
    var listOfString = ParameterizedTypeName.get(List.class, String.class);
    var prefixComparator = TypeSpec.anonymousClassBuilder("")
        .addSuperinterface(stringComparator)
        .addMethod(MethodSpec.methodBuilder("compare")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(int.class)
            .addParameter(String.class, "a")
            .addParameter(String.class, "b")
            .addStatement("return a.substring(0, length)\n"
                + ".compareTo(b.substring(0, length))")
            .build())
        .build();
    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(MethodSpec.methodBuilder("comparePrefix")
            .returns(stringComparator)
            .addParameter(int.class, "length", Modifier.FINAL)
            .addStatement("return $L", prefixComparator)
            .build())
        .addMethod(MethodSpec.methodBuilder("sortPrefix")
            .addParameter(listOfString, "list")
            .addParameter(int.class, "length", Modifier.FINAL)
            .addStatement("$T.sort(\nlist,\n$L)", Collections.class, prefixComparator)
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Override;
            import java.lang.String;
            import java.util.Collections;
            import java.util.Comparator;
            import java.util.List;

            class Taco {
              Comparator<String> comparePrefix(final int length) {
                return new Comparator<String>() {
                  @Override
                  public int compare(String a, String b) {
                    return a.substring(0, length)
                        .compareTo(b.substring(0, length));
                  }
                };
              }

              void sortPrefix(List<String> list, final int length) {
                Collections.sort(
                    list,
                    new Comparator<String>() {
                      @Override
                      public int compare(String a, String b) {
                        return a.substring(0, length)
                            .compareTo(b.substring(0, length));
                      }
                    });
              }
            }
            """);
  }

  @Test public void multilineStrings() throws Exception {
    var taco = TypeSpec.classBuilder("Taco")
        .addField(FieldSpec.builder(String.class, "toppings")
            .initializer("$S", "shell\nbeef\nlettuce\ncheese\n")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.String;

            class Taco {
              String toppings = "shell\\n"
                  + "beef\\n"
                  + "lettuce\\n"
                  + "cheese\\n";
            }
            """);
  }

  @Test public void doubleFieldInitialization() {
    assertThatThrownBy(() -> {
      FieldSpec.builder(String.class, "listA")
        .initializer("foo")
        .initializer("bar")
        .build();
    })
      .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() -> {
      FieldSpec.builder(String.class, "listA")
        .initializer(CodeBlock.builder().add("foo").build())
        .initializer(CodeBlock.builder().add("bar").build())
        .build();
    })
      .isInstanceOf(IllegalStateException.class);
  }

  @Test public void nullAnnotationsAddition() {
    assertThatThrownBy(() -> {
      TypeSpec.classBuilder("Taco").addAnnotations(null);
    })
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("annotationSpecs == null");
  }

  @Test public void multipleAnnotationAddition() {
    var taco = TypeSpec.classBuilder("Taco")
        .addAnnotations(List.of(
            AnnotationSpec.builder(SuppressWarnings.class)
                .addMember("value", "$S", "unchecked")
                .build(),
            AnnotationSpec.builder(Deprecated.class).build()))
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Deprecated;
            import java.lang.SuppressWarnings;

            @SuppressWarnings("unchecked")
            @Deprecated
            class Taco {
            }
            """);
  }

  @Test public void nullFieldsAddition() {
    assertThatThrownBy(() -> {
      TypeSpec.classBuilder("Taco").addFields(null);
    })
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("fieldSpecs == null");
  }

  @Test public void multipleFieldAddition() {
    var taco = TypeSpec.classBuilder("Taco")
        .addFields(List.of(
            FieldSpec.builder(int.class, "ANSWER", Modifier.STATIC, Modifier.FINAL).build(),
            FieldSpec.builder(BigDecimal.class, "price", Modifier.PRIVATE).build()))
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.math.BigDecimal;

            class Taco {
              static final int ANSWER;

              private BigDecimal price;
            }
            """);
  }

  @Test public void nullMethodsAddition() {
    assertThatThrownBy(() -> {
      TypeSpec.classBuilder("Taco").addMethods(null);
    })
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("methodSpecs == null");
  }

  @Test public void multipleMethodAddition() {
    var taco = TypeSpec.classBuilder("Taco")
        .addMethods(List.of(
            MethodSpec.methodBuilder("getAnswer")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(int.class)
                .addStatement("return $L", 42)
                .build(),
            MethodSpec.methodBuilder("getRandomQuantity")
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addJavadoc("chosen by fair dice roll ;)")
                .addStatement("return $L", 4)
                .build()))
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            class Taco {
              public static int getAnswer() {
                return 42;
              }

              /**
               * chosen by fair dice roll ;)
               */
              public int getRandomQuantity() {
                return 4;
              }
            }
            """);
  }

  @Test public void nullSuperinterfacesAddition() {
    assertThatThrownBy(() -> {
      TypeSpec.classBuilder("Taco").addSuperinterfaces(null);
    })
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("superinterfaces == null");
  }

  @Test public void nullSingleSuperinterfaceAddition() {
    assertThatThrownBy(() -> {
      TypeSpec.classBuilder("Taco").addSuperinterface((TypeName) null);
    })
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("superinterface == null");
  }

  @Test public void nullInSuperinterfaceIterableAddition() {
    var superinterfaces = Arrays.<TypeName>asList(
      TypeName.get(List.class),
      null
    );

    assertThatThrownBy(() -> {
      TypeSpec.classBuilder("Taco").addSuperinterfaces(superinterfaces);
    })
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("superinterface == null");
  }

  @Test public void multipleSuperinterfaceAddition() {
    var taco = TypeSpec.classBuilder("Taco")
        .addSuperinterfaces(List.of(
            TypeName.get(Serializable.class),
            TypeName.get(EventListener.class)))
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.io.Serializable;
            import java.util.EventListener;

            class Taco implements Serializable, EventListener {
            }
            """);
  }

  @Test public void nullModifiersAddition() {
    assertThatThrownBy(() -> {
      TypeSpec.classBuilder("Taco").addModifiers((Modifier) null).build();
    })
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("modifiers contain null");
  }

  @Test public void nullTypeVariablesAddition() {
    assertThatThrownBy(() -> {
      TypeSpec.classBuilder("Taco").addTypeVariables(null);
    })
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("typeVariables == null");
  }

  @Test public void multipleTypeVariableAddition() {
    var location = TypeSpec.classBuilder("Location")
        .addTypeVariables(List.of(
            TypeVariableName.get("T"),
            TypeVariableName.get("P", Number.class)))
        .build();
    assertThat(toString(location)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Number;

            class Location<T, P extends Number> {
            }
            """);
  }

  @Test public void nullTypesAddition() {
    assertThatThrownBy(() -> {
      TypeSpec.classBuilder("Taco").addTypes(null);
    })
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("typeSpecs == null");
  }

  @Test public void multipleTypeAddition() {
    var taco = TypeSpec.classBuilder("Taco")
        .addTypes(List.of(
            TypeSpec.classBuilder("Topping").build(),
            TypeSpec.classBuilder("Sauce").build()))
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            class Taco {
              class Topping {
              }

              class Sauce {
              }
            }
            """);
  }

  @Test public void tryCatch() {
    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(MethodSpec.methodBuilder("addTopping")
            .addParameter(ClassName.get("be.imgn.tacos", "Topping"), "topping")
            .beginControlFlow("try")
            .addCode("/* do something tricky with the topping */\n")
            .nextControlFlow("catch ($T e)",
                ClassName.get("be.imgn.tacos", "IllegalToppingException"))
            .endControlFlow()
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            class Taco {
              void addTopping(Topping topping) {
                try {
                  /* do something tricky with the topping */
                } catch (IllegalToppingException e) {
                }
              }
            }
            """);
  }

  @Test public void ifElse() {
    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(
            MethodSpec.methodBuilder("isDelicious")
                .addParameter(TypeName.INT, "count")
                .returns(TypeName.BOOLEAN)
                .beginControlFlow("if (count > 0)")
                .addStatement("return true")
                .nextControlFlow("else")
                .addStatement("return false")
                .endControlFlow()
                .build()
        )
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            class Taco {
              boolean isDelicious(int count) {
                if (count > 0) {
                  return true;
                } else {
                  return false;
                }
              }
            }
            """);
  }

  @Test public void literalFromAnything() {
    var value = new Object() {
      @Override public String toString() {
        return "foo";
      }
    };
    assertThat(CodeBlock.of("$L", value))
      .hasToString("foo");
  }

  @Test public void nameFromCharSequence() {
    assertThat(CodeBlock.of("$N", "text"))
      .hasToString("text");
  }

  @Test public void nameFromField() {
    var field = FieldSpec.builder(String.class, "field").build();
    assertThat(CodeBlock.of("$N", field))
      .hasToString("field");
  }

  @Test public void nameFromParameter() {
    var parameter = ParameterSpec.builder(String.class, "parameter").build();
    assertThat(CodeBlock.of("$N", parameter))
      .hasToString("parameter");
  }

  @Test public void nameFromMethod() {
    var method = MethodSpec.methodBuilder("method")
        .addModifiers(Modifier.ABSTRACT)
        .returns(String.class)
        .build();
    assertThat(CodeBlock.of("$N", method))
      .hasToString("method");
  }

  @Test public void nameFromType() {
    var type = TypeSpec.classBuilder("Type").build();
    assertThat(CodeBlock.of("$N", type))
      .hasToString("Type");
  }

  @Test public void nameFromUnsupportedType() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$N", String.class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("expected name but was " + String.class);
  }

  @Test public void stringFromAnything() {
    var value = new Object() {
      @Override public String toString() {
        return "foo";
      }
    };
    assertThat(CodeBlock.of("$S", value))
      .hasToString("\"foo\"");
  }

  @Test public void stringFromNull() {
    assertThat(CodeBlock.of("$S", new Object[] {null}))
      .hasToString("null");
  }

  @Test public void typeFromTypeName() {
    var typeName = TypeName.get(String.class);
    assertThat(CodeBlock.of("$T", typeName))
      .hasToString("java.lang.String");
  }

  @Test public void typeFromTypeMirror() {
    var mirror = getElement(String.class).asType();
    assertThat(CodeBlock.of("$T", mirror))
      .hasToString("java.lang.String");
  }

  @Test public void typeFromTypeElement() {
    var element = getElement(String.class);
    assertThat(CodeBlock.of("$T", element))
      .hasToString("java.lang.String");
  }

  @Test public void typeFromReflectType() {
    assertThat(CodeBlock.of("$T", String.class))
      .hasToString("java.lang.String");
  }

  @Test public void typeFromUnsupportedType() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$T", "java.lang.String"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("expected type but was java.lang.String");
  }

  @Test public void tooFewArguments() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$S"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("index 1 for '$S' not in range (received 0 arguments)");
  }

  @Test public void unusedArgumentsRelative() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$L $L", "a", "b", "c"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("unused arguments: expected 2, received 3");
  }

  @Test public void unusedArgumentsIndexed() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$1L $2L", "a", "b", "c"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("unused argument: $3");
    assertThatThrownBy(() -> CodeBlock.builder().add("$1L $1L $1L", "a", "b", "c"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("unused arguments: $2, $3");
    assertThatThrownBy(() -> CodeBlock.builder().add("$3L $1L $3L $1L $3L", "a", "b", "c", "d"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("unused arguments: $2, $4");
  }

  @Test public void superClassOnlyValidForClasses() {
    assertThatThrownBy(() -> TypeSpec.annotationBuilder("A").superclass(ClassName.get(Object.class)))
      .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> TypeSpec.enumBuilder("E").superclass(ClassName.get(Object.class)))
      .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> TypeSpec.interfaceBuilder("I").superclass(ClassName.get(Object.class)))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test public void invalidSuperClass() {
    assertThatThrownBy(() -> {
      TypeSpec.classBuilder("foo")
        .superclass(ClassName.get(List.class))
        .superclass(ClassName.get(Map.class));
    })
      .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> {
      TypeSpec.classBuilder("foo")
        .superclass(TypeName.INT);
    })
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test public void staticCodeBlock() {
    var taco = TypeSpec.classBuilder("Taco")
        .addField(String.class, "foo", Modifier.PRIVATE)
        .addField(String.class, "FOO", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .addStaticBlock(CodeBlock.builder()
            .addStatement("FOO = $S", "FOO")
            .build())
        .addMethod(MethodSpec.methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addCode("return FOO;\n")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Override;
            import java.lang.String;

            class Taco {
              private static final String FOO;

              static {
                FOO = "FOO";
              }

              private String foo;

              @Override
              public String toString() {
                return FOO;
              }
            }
            """);
  }

  @Test public void initializerBlockInRightPlace() {
    var taco = TypeSpec.classBuilder("Taco")
        .addField(String.class, "foo", Modifier.PRIVATE)
        .addField(String.class, "FOO", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .addStaticBlock(CodeBlock.builder()
            .addStatement("FOO = $S", "FOO")
            .build())
        .addMethod(MethodSpec.constructorBuilder().build())
        .addMethod(MethodSpec.methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addCode("return FOO;\n")
            .build())
        .addInitializerBlock(CodeBlock.builder()
            .addStatement("foo = $S", "FOO")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Override;
            import java.lang.String;

            class Taco {
              private static final String FOO;

              static {
                FOO = "FOO";
              }

              private String foo;

              {
                foo = "FOO";
              }

              Taco() {
              }

              @Override
              public String toString() {
                return FOO;
              }
            }
            """);
  }

  @Test public void initializersToBuilder() {
    // Tests if toBuilder() contains correct static and instance initializers
    var originatingElement = getElement(TypeSpecTest.class);
    var taco = TypeSpec.classBuilder("Taco")
        .addField(String.class, "foo", Modifier.PRIVATE)
        .addField(String.class, "FOO", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .addStaticBlock(CodeBlock.builder()
            .addStatement("FOO = $S", "FOO")
            .build())
        .addMethod(MethodSpec.constructorBuilder().build())
        .addMethod(MethodSpec.methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addCode("return FOO;\n")
            .build())
        .addInitializerBlock(CodeBlock.builder()
            .addStatement("foo = $S", "FOO")
            .build())
        .addOriginatingElement(originatingElement)
        .alwaysQualify("com.example.AlwaysQualified")
        .build();

    var recreatedTaco = taco.toBuilder().build();
    assertThat(toString(taco))
      .isEqualTo(toString(recreatedTaco));
    assertThat(taco.originatingElements)
      .containsExactlyElementsOf(recreatedTaco.originatingElements);
    assertThat(taco.alwaysQualifiedNames)
      .containsExactlyElementsOf(recreatedTaco.alwaysQualifiedNames);

    var initializersAdded = taco.toBuilder()
        .addInitializerBlock(CodeBlock.builder()
            .addStatement("foo = $S", "instanceFoo")
            .build())
        .addStaticBlock(CodeBlock.builder()
            .addStatement("FOO = $S", "staticFoo")
            .build())
        .build();

    assertThat(toString(initializersAdded)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.Override;
            import java.lang.String;

            class Taco {
              private static final String FOO;

              static {
                FOO = "FOO";
              }
              static {
                FOO = "staticFoo";
              }

              private String foo;

              {
                foo = "FOO";
              }
              {
                foo = "instanceFoo";
              }

              Taco() {
              }

              @Override
              public String toString() {
                return FOO;
              }
            }
            """);
  }

  @Test public void initializerBlockUnsupportedExceptionOnInterface() {
    var interfaceBuilder = TypeSpec.interfaceBuilder("Taco");
    assertThatThrownBy(() -> interfaceBuilder.addInitializerBlock(CodeBlock.builder().build()))
      .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test public void initializerBlockUnsupportedExceptionOnAnnotation() {
    var annotationBuilder = TypeSpec.annotationBuilder("Taco");
    assertThatThrownBy(() -> annotationBuilder.addInitializerBlock(CodeBlock.builder().build()))
      .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test public void lineWrapping() {
    var methodBuilder = MethodSpec.methodBuilder("call");
    methodBuilder.addCode("$[call(");
    for (var i = 0; i < 32; i++) {
      methodBuilder.addParameter(String.class, "s" + i);
      methodBuilder.addCode(i > 0 ? ",$W$S" : "$S", i);
    }
    methodBuilder.addCode(");$]\n");

    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(methodBuilder.build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            import java.lang.String;

            class Taco {
              void call(String s0, String s1, String s2, String s3, String s4, String s5, String s6, String s7,
                  String s8, String s9, String s10, String s11, String s12, String s13, String s14, String s15,
                  String s16, String s17, String s18, String s19, String s20, String s21, String s22,
                  String s23, String s24, String s25, String s26, String s27, String s28, String s29,
                  String s30, String s31) {
                call("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
                    "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31");
              }
            }
            """);
  }

  @Test public void lineWrappingWithZeroWidthSpace() {
    var method = MethodSpec.methodBuilder("call")
        .addCode("$[iAmSickOfWaitingInLine($Z")
        .addCode("it, has, been, far, too, long, of, a, wait, and, i, would, like, to, eat, ")
        .addCode("this, is, a, run, on, sentence")
        .addCode(");$]\n")
        .build();

    var taco = TypeSpec.classBuilder("Taco")
        .addMethod(method)
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;

            class Taco {
              void call() {
                iAmSickOfWaitingInLine(
                    it, has, been, far, too, long, of, a, wait, and, i, would, like, to, eat, this, is, a, run, on, sentence);
              }
            }
            """);
  }

  @Test public void equalsAndHashCode() {
    var a = TypeSpec.interfaceBuilder("taco").build();
    var b = TypeSpec.interfaceBuilder("taco").build();
    assertThat(a)
      .isEqualTo(b)
      .hasSameHashCodeAs(b);
    a = TypeSpec.classBuilder("taco").build();
    b = TypeSpec.classBuilder("taco").build();
    assertThat(a)
      .isEqualTo(b)
      .hasSameHashCodeAs(b);
    a = TypeSpec.enumBuilder("taco").addEnumConstant("SALSA").build();
    b = TypeSpec.enumBuilder("taco").addEnumConstant("SALSA").build();
    assertThat(a)
      .isEqualTo(b)
      .hasSameHashCodeAs(b);
    a = TypeSpec.annotationBuilder("taco").build();
    b = TypeSpec.annotationBuilder("taco").build();
    assertThat(a)
      .isEqualTo(b)
      .hasSameHashCodeAs(b);
  }

  @Test public void classNameFactories() {
    var className = ClassName.get("com.example", "Example");
    assertThat(TypeSpec.classBuilder(className).build().name).isEqualTo("Example");
    assertThat(TypeSpec.interfaceBuilder(className).build().name).isEqualTo("Example");
    assertThat(TypeSpec.enumBuilder(className).addEnumConstant("A").build().name).isEqualTo("Example");
    assertThat(TypeSpec.annotationBuilder(className).build().name).isEqualTo("Example");
  }

  @Test
  public void modifyAnnotations() {
    var builder =
        TypeSpec.classBuilder("Taco")
            .addAnnotation(Override.class)
            .addAnnotation(SuppressWarnings.class);

    builder.annotations.remove(1);
    assertThat(builder.build().annotations).hasSize(1);
  }

  @Test
  public void modifyModifiers() {
    var builder =
        TypeSpec.classBuilder("Taco").addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    builder.modifiers.remove(1);
    assertThat(builder.build().modifiers).containsExactly(Modifier.PUBLIC);
  }

  @Test
  public void modifyFields() {
    var builder = TypeSpec.classBuilder("Taco")
        .addField(int.class, "source");

    builder.fieldSpecs.remove(0);
    assertThat(builder.build().fieldSpecs).isEmpty();
  }

  @Test
  public void modifyTypeVariables() {
    var t = TypeVariableName.get("T");
    var builder =
        TypeSpec.classBuilder("Taco")
            .addTypeVariable(t)
            .addTypeVariable(TypeVariableName.get("V"));

    builder.typeVariables.remove(1);
    assertThat(builder.build().typeVariables).containsExactly(t);
  }

  @Test
  public void modifySuperinterfaces() {
    var builder = TypeSpec.classBuilder("Taco")
        .addSuperinterface(File.class);

    builder.superinterfaces.clear();
    assertThat(builder.build().superinterfaces).isEmpty();
  }

  @Test
  public void modifyMethods() {
    var builder = TypeSpec.classBuilder("Taco")
        .addMethod(MethodSpec.methodBuilder("bell").build());

    builder.methodSpecs.clear();
    assertThat(builder.build().methodSpecs).isEmpty();
  }

  @Test
  public void modifyTypes() {
    var builder = TypeSpec.classBuilder("Taco")
        .addType(TypeSpec.classBuilder("Bell").build());

    builder.typeSpecs.clear();
    assertThat(builder.build().typeSpecs).isEmpty();
  }

  @Test
  public void modifyEnumConstants() {
    var constantType = TypeSpec.anonymousClassBuilder("").build();
    var builder = TypeSpec.enumBuilder("Taco")
        .addEnumConstant("BELL", constantType)
        .addEnumConstant("WUT", TypeSpec.anonymousClassBuilder("").build());

    builder.enumConstants.remove("WUT");
    assertThat(builder.build().enumConstants)
      .containsExactly(entry("BELL", constantType));
  }

  @Test
  public void modifyOriginatingElements() {
    var builder = TypeSpec.classBuilder("Taco")
        .addOriginatingElement(Mockito.mock(Element.class));

    builder.originatingElements.clear();
    assertThat(builder.build().originatingElements).isEmpty();
  }

  @Test public void javadocWithTrailingLineDoesNotAddAnother() {
    var spec = TypeSpec.classBuilder("Taco")
        .addJavadoc("Some doc with a newline\n")
        .build();

    assertThat(toString(spec)).isEqualTo("""
            package be.imgn.tacos;

            /**
             * Some doc with a newline
             */
            class Taco {
            }
            """);
  }

  @Test public void javadocEnsuresTrailingLine() {
    var spec = TypeSpec.classBuilder("Taco")
        .addJavadoc("Some doc with a newline")
        .build();

    assertThat(toString(spec)).isEqualTo("""
            package be.imgn.tacos;

            /**
             * Some doc with a newline
             */
            class Taco {
            }
            """);
  }

  @Test public void simpleRecord() {
    var point = TypeSpec.recordBuilder("Taco")
        .recordConstructor(MethodSpec.constructorBuilder()
            .addParameter(String.class, "a")
            .addParameter(long.class, "b")
            .build())
        .build();

    var javaFile = JavaFile.builder("be.imgn.tacos", point)
        .skipJavaLangImports(true)
        .build();

    assertThat(javaFile.toString()).isEqualTo("""
        package be.imgn.tacos;

        record Taco(String a, long b) {
        }
        """);
  }

  @Test public void recordWithCompactConstructor() {
    var person = TypeSpec.recordBuilder("Taco")
        .recordConstructor(MethodSpec.compactConstructorBuilder()
            .addParameter(String.class, "one")
            .addParameter(int.class, "two")
            .addCode("""
                /* snippet */
                """, IllegalArgumentException.class)
            .build())
        .addMethod(MethodSpec.methodBuilder("method")
            .addModifiers(Modifier.PUBLIC)
            .returns(boolean.class)
            .addStatement("return true")
            .build())
        .build();

    var javaFile = JavaFile.builder("be.imgn.tacos", person)
        .skipJavaLangImports(true)
        .build();

    assertThat(javaFile.toString())
      .isEqualTo("""
        package be.imgn.tacos;

        record Taco(String one, int two) {
          Taco {
            /* snippet */
          }

          public boolean method() {
            return true;
          }
        }
        """);
  }

  @Test public void recordWithZeroParameters() {
    var empty = TypeSpec.recordBuilder("Taco")
        .recordConstructor(MethodSpec.constructorBuilder()
            .build())
        .build();

    var javaFile = JavaFile.builder("be.imgn.tacos", empty)
        .skipJavaLangImports(true)
        .build();

    assertThat(javaFile.toString()).isEqualTo("""
        package be.imgn.tacos;

        record Taco() {
        }
        """);
  }

  @Test public void recordWithJavadoc() {
    var point = TypeSpec.recordBuilder("Taco")
        .addJavadoc("Tacocat.")
        .recordConstructor(MethodSpec.constructorBuilder()
            .addParameter(ParameterSpec.builder(String.class, "one")
                .addJavadoc("the one value\n")
                .build())
            .addParameter(ParameterSpec.builder(long.class, "two")
                .addJavadoc("the two value\n")
                .build())
            .build())
        .build();

    var javaFile = JavaFile.builder("be.imgn.tacos", point)
        .skipJavaLangImports(true)
        .build();

    assertThat(javaFile.toString())
      .isEqualTo("""
        package be.imgn.tacos;

        /**
         * Tacocat.
         * @param one the one value
         * @param two the two value
         */
        record Taco(String one, long two) {
        }
        """);
  }

  @Test public void recordWithPartialJavadoc() {
    var person = TypeSpec.recordBuilder("Person")
        .addJavadoc("Represents a person with optional metadata.\n")
        .recordConstructor(MethodSpec.constructorBuilder()
            .addParameter(ParameterSpec.builder(String.class, "name")
                .addJavadoc("the person's full name\n")
                .build())
            .addParameter(int.class, "age") // No javadoc for this parameter
            .addParameter(ParameterSpec.builder(String.class, "email")
                .addJavadoc("the person's email address\n")
                .build())
            .build())
        .build();

    var javaFile = JavaFile.builder("be.imgn.tacos", person)
        .skipJavaLangImports(true)
        .build();

    assertThat(javaFile)
      .hasToString("""
        package be.imgn.tacos;

        /**
         * Represents a person with optional metadata.
         *
         * @param name the person's full name
         * @param email the person's email address
         */
        record Person(String name, int age, String email) {
        }
        """);
  }

  @Test public void recordWithAdditionalConstructor() {
    var person = TypeSpec.recordBuilder("Person")
        .recordConstructor(MethodSpec.compactConstructorBuilder()
            .addParameter(String.class, "name")
            .addParameter(int.class, "age")
            .addCode("""
                requireNonNull(name);
                if (age < 0) throw new $T();
                """, IllegalArgumentException.class)
            .build())
        .addMethod(MethodSpec.constructorBuilder()
            .addParameter(String.class, "name")
            .addStatement("this(name, 0)")
            .build())
        .build();

    var javaFile = JavaFile.builder("be.imgn.tacos", person)
        .skipJavaLangImports(true)
        .build();

    assertThat(javaFile)
      .hasToString("""
        package be.imgn.tacos;

        record Person(String name, int age) {
          Person {
            requireNonNull(name);
            if (age < 0) throw new IllegalArgumentException();
          }

          Person(String name) {
            this(name, 0);
          }
        }
        """);
  }
}
