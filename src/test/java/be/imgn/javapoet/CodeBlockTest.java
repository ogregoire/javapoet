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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class CodeBlockTest {
  @Test public void equalsAndHashCode() {
    var a = CodeBlock.builder().build();
    var b = CodeBlock.builder().build();
    assertThat(a)
      .isEqualTo(b)
      .hasSameHashCodeAs(b);
    a = CodeBlock.builder().add("$L", "taco").build();
    b = CodeBlock.builder().add("$L", "taco").build();
    assertThat(a)
      .isEqualTo(b)
      .hasSameHashCodeAs(b);
  }

  @Test public void of() {
    var a = CodeBlock.of("$L taco", "delicious");
    assertThat(a)
      .hasToString("delicious taco");
  }

  @Test public void isEmpty() {
    assertThat(CodeBlock.builder().isEmpty())
      .isTrue();
    assertThat(CodeBlock.builder().add("").isEmpty())
      .isTrue();
    assertThat(CodeBlock.builder().add(" ").isEmpty())
      .isFalse();
  }

  @ParameterizedTest
  @MethodSource("provideFormatsCannotBeIndexed")
  public void formatCannotBeIndexed(String format) {
    assertThatThrownBy(() -> CodeBlock.builder().add(format, "taco").build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("$$, $>, $<, $[, $], $W, and $Z may not have an index");
  }

  private static List<Arguments> provideFormatsCannotBeIndexed() {
    return List.of(
      Arguments.of("$1>"), // indent
      Arguments.of("$1<"), // deindent
      Arguments.of("$1$"), // dollar sign escape
      Arguments.of("$1["), // statement beginning
      Arguments.of("$1]")  // statement ending
    );
  }

  @ParameterizedTest
  @MethodSource("provideFormatsCanBeIndexed")
  public void formatCanBeIndexed(String format, Object argument, String expectedOutput) {
    var block = CodeBlock.builder().add(format, argument).build();
    assertThat(block)
      .hasToString(expectedOutput);
  }

  private static List<Arguments> provideFormatsCanBeIndexed() {
    return List.of(
      Arguments.of("$1N", "taco", "taco"),              // name format
      Arguments.of("$1L", "taco", "taco"),              // literal format
      Arguments.of("$1S", "taco", "\"taco\""),          // string format
      Arguments.of("$1T", String.class, "java.lang.String") // type format
    );
  }

  @Test public void simpleNamedArgument() {
    var map = new LinkedHashMap<String, Object>();
    map.put("text", "taco");
    var block = CodeBlock.builder().addNamed("$text:S", map).build();
    assertThat(block)
      .hasToString("\"taco\"");
  }

  @Test public void repeatedNamedArgument() {
    var map = new LinkedHashMap<String, Object>();
    map.put("text", "tacos");
    var block = CodeBlock.builder()
        .addNamed("\"I like \" + $text:S + \". Do you like \" + $text:S + \"?\"", map)
        .build();
    assertThat(block)
      .hasToString("\"I like \" + \"tacos\" + \". Do you like \" + \"tacos\" + \"?\"");
  }

  @Test public void namedAndNoArgFormat() {
    var map = new LinkedHashMap<String, Object>();
    map.put("text", "tacos");
    var block = CodeBlock.builder()
        .addNamed("$>\n$text:L for $$3.50", map).build();
    assertThat(block)
      .hasToString("\n  tacos for $3.50");
  }

  @Test public void missingNamedArgument() {
    assertThatThrownBy(() -> CodeBlock.builder().addNamed("$text:S", Map.of()).build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Missing named argument for $text");
  }

  @Test public void lowerCaseNamed() {
    assertThatThrownBy(() -> CodeBlock.builder().addNamed("$text:S", Map.of()).build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Missing named argument for $text");
  }

  @Test public void multipleNamedArguments() {
    var map = Map.of(
      "pipe", System.class,
      "text", "tacos");

    var block = CodeBlock.builder()
        .addNamed("$pipe:T.out.println(\"Let's eat some $text:L\");", map)
        .build();

    assertThat(block)
      .hasToString(
        "java.lang.System.out.println(\"Let's eat some tacos\");");
  }

  @Test public void namedNewline() {
    var map = Map.of("clazz", Integer.class);
    var block = CodeBlock.builder().addNamed("$clazz:T\n", map).build();
    assertThat(block)
      .hasToString("java.lang.Integer\n");
  }

  @Test public void danglingNamed() {
    var map = Map.of("clazz", Integer.class);
    assertThatThrownBy(() -> CodeBlock.builder().addNamed("$clazz:T$", map).build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("dangling $ at end");
  }

  @Test public void indexTooHigh() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$2T", String.class).build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("index 2 for '$2T' not in range (received 1 arguments)");
  }

  @Test public void indexIsZero() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$0T", String.class).build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("index 0 for '$0T' not in range (received 1 arguments)");
  }

  @Test public void indexIsNegative() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$-1T", String.class).build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("invalid format string: '$-1T'");
  }

  @Test public void indexWithoutFormatType() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$1", String.class).build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("dangling format characters in '$1'");
  }

  @Test public void indexWithoutFormatTypeNotAtStringEnd() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$1 taco", String.class).build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("invalid format string: '$1 taco'");
  }

  @Test public void indexButNoArguments() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$1T").build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("index 1 for '$1T' not in range (received 0 arguments)");
  }

  @Test public void formatIndicatorAlone() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$", String.class).build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("dangling format characters in '$'");
  }

  @Test public void formatIndicatorWithoutIndexOrFormatType() {
    assertThatThrownBy(() -> CodeBlock.builder().add("$ tacoString", String.class).build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("invalid format string: '$ tacoString'");
  }

  @Test public void sameIndexCanBeUsedWithDifferentFormats() {
    var block = CodeBlock.builder()
        .add("$1T.out.println($1S)", ClassName.get(System.class))
        .build();
    assertThat(block)
      .hasToString("java.lang.System.out.println(\"java.lang.System\")");
  }

  @Test public void tooManyStatementEnters() {
    var codeBlock = CodeBlock.builder().add("$[$[").build();
    // We can't report this error until rendering type because code blocks might be composed.
    assertThatThrownBy(() -> codeBlock.toString())
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("statement enter $[ followed by statement enter $[");
  }

  @Test public void statementExitWithoutStatementEnter() {
    var codeBlock = CodeBlock.builder().add("$]").build();
    // We can't report this error until rendering type because code blocks might be composed.
    assertThatThrownBy(() -> codeBlock.toString())
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("statement exit $] has no matching statement enter $[");
  }

  @Test public void join() {
    var codeBlocks = List.of(
      CodeBlock.of("$S", "hello"),
      CodeBlock.of("$T", ClassName.get("world", "World")),
      CodeBlock.of("need tacos"));

    var joined = CodeBlock.join(codeBlocks, " || ");
    assertThat(joined)
      .hasToString("\"hello\" || world.World || need tacos");
  }

  @Test public void joining() {
    var codeBlocks = List.of(
      CodeBlock.of("$S", "hello"),
      CodeBlock.of("$T", ClassName.get("world", "World")),
      CodeBlock.of("need tacos"));

    var joined = codeBlocks.stream().collect(CodeBlock.joining(" || "));
    assertThat(joined)
      .hasToString("\"hello\" || world.World || need tacos");
  }

  @Test public void joiningSingle() {
    var codeBlocks = List.of(CodeBlock.of("$S", "hello"));

    var joined = codeBlocks.stream().collect(CodeBlock.joining(" || "));
    assertThat(joined)
      .hasToString("\"hello\"");
  }

  @Test public void joiningWithPrefixAndSuffix() {
    var codeBlocks = List.of(
      CodeBlock.of("$S", "hello"),
      CodeBlock.of("$T", ClassName.get("world", "World")),
      CodeBlock.of("need tacos"));

    var joined = codeBlocks.stream().collect(CodeBlock.joining(" || ", "start {", "} end"));
    assertThat(joined)
      .hasToString("start {\"hello\" || world.World || need tacos} end");
  }

  @Test public void clear() {
    var block = CodeBlock.builder()
        .addStatement("$S", "Test string")
        .clear()
        .build();

    assertThat(block.toString())
      .isEmpty();
  }
}
