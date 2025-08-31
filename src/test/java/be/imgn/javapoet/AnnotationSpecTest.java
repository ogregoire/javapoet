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

import com.google.testing.compile.CompilationRule;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

import javax.lang.model.element.TypeElement;
import org.junit.Rule;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public final class AnnotationSpecTest {

  @Retention(RetentionPolicy.RUNTIME)
  public @interface AnnotationA {
  }

  @Inherited
  @Retention(RetentionPolicy.RUNTIME)
  public @interface AnnotationB {
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface AnnotationC {
    String value();
  }

  public enum Breakfast {
    WAFFLES, PANCAKES;
    public String toString() { return name() + " with cherries!"; };
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface HasDefaultsAnnotation {

    byte a() default 5;

    short b() default 6;

    int c() default 7;

    long d() default 12345678910L;

    float e() default 9.0f;

    double f() default 10.0;

    char[] g() default {0, 0xCAFE, 'z', '€', 'ℕ', '"', '\'', '\t', '\n'};

    boolean h() default true;

    Breakfast i() default Breakfast.WAFFLES;

    AnnotationA j() default @AnnotationA();

    String k() default "maple";

    Class<? extends Annotation> l() default AnnotationB.class;

    int[] m() default {1, 2, 3};

    Breakfast[] n() default {Breakfast.WAFFLES, Breakfast.PANCAKES};

    Breakfast o();

    int p();

    AnnotationC q() default @AnnotationC("foo");

    Class<? extends Number>[] r() default {Byte.class, Short.class, Integer.class, Long.class};

  }

  @HasDefaultsAnnotation(
      o = Breakfast.PANCAKES,
      p = 1701,
      f = 11.1,
      m = {9, 8, 1},
      l = Override.class,
      j = @AnnotationA,
      q = @AnnotationC("bar"),
      r = {Float.class, Double.class})
  public class IsAnnotated {
    // empty
  }

  @Rule public final CompilationRule compilation = new CompilationRule();

  @Test public void equalsAndHashCode() {
    var a = AnnotationSpec.builder(AnnotationC.class).build();
    var b = AnnotationSpec.builder(AnnotationC.class).build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    a = AnnotationSpec.builder(AnnotationC.class).addMember("value", "$S", "123").build();
    b = AnnotationSpec.builder(AnnotationC.class).addMember("value", "$S", "123").build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test public void defaultAnnotation() {
    var name = IsAnnotated.class.getCanonicalName();
    var element = compilation.getElements().getTypeElement(name);
    var annotation = AnnotationSpec.get(element.getAnnotationMirrors().getFirst());

    var taco = TypeSpec.classBuilder("Taco")
        .addAnnotation(annotation)
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;
            
            import be.imgn.javapoet.AnnotationSpecTest;
            import java.lang.Double;
            import java.lang.Float;
            import java.lang.Override;
            
            @AnnotationSpecTest.HasDefaultsAnnotation(
                o = AnnotationSpecTest.Breakfast.PANCAKES,
                p = 1701,
                f = 11.1,
                m = {
                    9,
                    8,
                    1
                },
                l = Override.class,
                j = @AnnotationSpecTest.AnnotationA,
                q = @AnnotationSpecTest.AnnotationC("bar"),
                r = {
                    Float.class,
                    Double.class
                }
            )
            class Taco {
            }
            """);
  }

  @Test public void defaultAnnotationWithImport() {
    var name = IsAnnotated.class.getCanonicalName();
    var element = compilation.getElements().getTypeElement(name);
    var annotation = AnnotationSpec.get(element.getAnnotationMirrors().get(0));
    var typeBuilder = TypeSpec.classBuilder(IsAnnotated.class.getSimpleName());
    typeBuilder.addAnnotation(annotation);
    var file = JavaFile.builder("be.imgn.javapoet", typeBuilder.build()).build();
    assertThat(file.toString()).isEqualTo(
            """
                    package be.imgn.javapoet;
                    
                    import java.lang.Double;
                    import java.lang.Float;
                    import java.lang.Override;
                    
                    @AnnotationSpecTest.HasDefaultsAnnotation(
                        o = AnnotationSpecTest.Breakfast.PANCAKES,
                        p = 1701,
                        f = 11.1,
                        m = {
                            9,
                            8,
                            1
                        },
                        l = Override.class,
                        j = @AnnotationSpecTest.AnnotationA,
                        q = @AnnotationSpecTest.AnnotationC("bar"),
                        r = {
                            Float.class,
                            Double.class
                        }
                    )
                    class IsAnnotated {
                    }
                    """
    );
  }

  @Test public void emptyArray() {
    var builder = AnnotationSpec.builder(HasDefaultsAnnotation.class);
    builder.addMember("n", "$L", "{}");
    assertThat(builder.build().toString()).isEqualTo(
        "@be.imgn.javapoet.AnnotationSpecTest.HasDefaultsAnnotation(" + "n = {}" + ")");
    builder.addMember("m", "$L", "{}");
    assertThat(builder.build().toString())
        .isEqualTo(
            "@be.imgn.javapoet.AnnotationSpecTest.HasDefaultsAnnotation("
                + "n = {}, m = {}"
                + ")");
  }

  @Test public void dynamicArrayOfEnumConstants() {
    var builder = AnnotationSpec.builder(HasDefaultsAnnotation.class);
    builder.addMember("n", "$T.$L", Breakfast.class, Breakfast.PANCAKES.name());
    assertThat(builder.build().toString()).isEqualTo(
        "@be.imgn.javapoet.AnnotationSpecTest.HasDefaultsAnnotation("
            + "n = be.imgn.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
            + ")");

    // builder = AnnotationSpec.builder(HasDefaultsAnnotation.class);
    builder.addMember("n", "$T.$L", Breakfast.class, Breakfast.WAFFLES.name());
    builder.addMember("n", "$T.$L", Breakfast.class, Breakfast.PANCAKES.name());
    assertThat(builder.build().toString()).isEqualTo(
        "@be.imgn.javapoet.AnnotationSpecTest.HasDefaultsAnnotation("
            + "n = {"
            + "be.imgn.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
            + ", be.imgn.javapoet.AnnotationSpecTest.Breakfast.WAFFLES"
            + ", be.imgn.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
            + "})");

    builder = builder.build().toBuilder(); // idempotent
    assertThat(builder.build().toString()).isEqualTo(
        "@be.imgn.javapoet.AnnotationSpecTest.HasDefaultsAnnotation("
            + "n = {"
            + "be.imgn.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
            + ", be.imgn.javapoet.AnnotationSpecTest.Breakfast.WAFFLES"
            + ", be.imgn.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
            + "})");

    builder.addMember("n", "$T.$L", Breakfast.class, Breakfast.WAFFLES.name());
    assertThat(builder.build().toString()).isEqualTo(
        "@be.imgn.javapoet.AnnotationSpecTest.HasDefaultsAnnotation("
            + "n = {"
            + "be.imgn.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
            + ", be.imgn.javapoet.AnnotationSpecTest.Breakfast.WAFFLES"
            + ", be.imgn.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
            + ", be.imgn.javapoet.AnnotationSpecTest.Breakfast.WAFFLES"
            + "})");
  }

  @Test public void defaultAnnotationToBuilder() {
    var name = IsAnnotated.class.getCanonicalName();
    var element = compilation.getElements().getTypeElement(name);
    var builder = AnnotationSpec.get(element.getAnnotationMirrors().get(0))
        .toBuilder();
    builder.addMember("m", "$L", 123);
    assertThat(builder.build().toString()).isEqualTo(
        "@be.imgn.javapoet.AnnotationSpecTest.HasDefaultsAnnotation("
            + "o = be.imgn.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
            + ", p = 1701"
            + ", f = 11.1"
            + ", m = {9, 8, 1, 123}"
            + ", l = java.lang.Override.class"
            + ", j = @be.imgn.javapoet.AnnotationSpecTest.AnnotationA"
            + ", q = @be.imgn.javapoet.AnnotationSpecTest.AnnotationC(\"bar\")"
            + ", r = {java.lang.Float.class, java.lang.Double.class}"
            + ")");
  }

  @Test public void reflectAnnotation() {
    var annotation = IsAnnotated.class.getAnnotation(HasDefaultsAnnotation.class);
    var spec = AnnotationSpec.get(annotation);
    var taco = TypeSpec.classBuilder("Taco")
        .addAnnotation(spec)
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;
            
            import be.imgn.javapoet.AnnotationSpecTest;
            import java.lang.Double;
            import java.lang.Float;
            import java.lang.Override;
            
            @AnnotationSpecTest.HasDefaultsAnnotation(
                f = 11.1,
                l = Override.class,
                m = {
                    9,
                    8,
                    1
                },
                o = AnnotationSpecTest.Breakfast.PANCAKES,
                p = 1701,
                q = @AnnotationSpecTest.AnnotationC("bar"),
                r = {
                    Float.class,
                    Double.class
                }
            )
            class Taco {
            }
            """);
  }

  @Test public void reflectAnnotationWithDefaults() {
    var annotation = IsAnnotated.class.getAnnotation(HasDefaultsAnnotation.class);
    var spec = AnnotationSpec.get(annotation, true);
    var taco = TypeSpec.classBuilder("Taco")
        .addAnnotation(spec)
        .build();
    assertThat(toString(taco)).isEqualTo("""
            package be.imgn.tacos;
            
            import be.imgn.javapoet.AnnotationSpecTest;
            import java.lang.Double;
            import java.lang.Float;
            import java.lang.Override;
            
            @AnnotationSpecTest.HasDefaultsAnnotation(
                a = 5,
                b = 6,
                c = 7,
                d = 12345678910L,
                e = 9.0f,
                f = 11.1,
                g = {
                    '\\u0000',
                    '쫾',
                    'z',
                    '€',
                    'ℕ',
                    '"',
                    '\\'',
                    '\\t',
                    '\\n'
                },
                h = true,
                i = AnnotationSpecTest.Breakfast.WAFFLES,
                j = @AnnotationSpecTest.AnnotationA,
                k = "maple",
                l = Override.class,
                m = {
                    9,
                    8,
                    1
                },
                n = {
                    AnnotationSpecTest.Breakfast.WAFFLES,
                    AnnotationSpecTest.Breakfast.PANCAKES
                },
                o = AnnotationSpecTest.Breakfast.PANCAKES,
                p = 1701,
                q = @AnnotationSpecTest.AnnotationC("bar"),
                r = {
                    Float.class,
                    Double.class
                }
            )
            class Taco {
            }
            """);
  }

  @Test public void disallowsNullMemberName() {
    var builder = AnnotationSpec.builder(HasDefaultsAnnotation.class);
    try {
      AnnotationSpec.Builder $L = builder.addMember(null, "$L", "");
      fail($L.build().toString());
    } catch (NullPointerException e) {
      assertThat(e).hasMessageThat().isEqualTo("name == null");
    }
  }

  @Test public void requiresValidMemberName() {
    var builder = AnnotationSpec.builder(HasDefaultsAnnotation.class);
    try {
      var $L = builder.addMember("@", "$L", "");
      fail($L.build().toString());
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().isEqualTo("not a valid name: @");
    }
  }

  @Test public void modifyMembers() {
    var builder = AnnotationSpec.builder(SuppressWarnings.class)
            .addMember("value", "$S", "Foo");
    
    builder.members.clear();
    builder.members.put("value", Arrays.asList(CodeBlock.of("$S", "Bar")));

    assertThat(builder.build().toString()).isEqualTo("@java.lang.SuppressWarnings(\"Bar\")");
  }

  private String toString(TypeSpec typeSpec) {
    return JavaFile.builder("be.imgn.tacos", typeSpec).build().toString();
  }
}
