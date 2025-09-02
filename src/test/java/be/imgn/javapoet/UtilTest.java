/*
 * Copyright (C) 2016 Square, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class UtilTest {
  @ParameterizedTest
  @MethodSource("provideCharacterLiterals")
  public void characterLiteral(String expected, char inputChar) {
    assertThat(expected)
      .isEqualTo(Util.characterLiteralWithoutSingleQuotes(inputChar));
  }

  private static List<Arguments> provideCharacterLiterals() {
    return List.of(
      // normal characters
      Arguments.of("a", 'a'),
      Arguments.of("b", 'b'),
      Arguments.of("c", 'c'),
      Arguments.of("%", '%'),
      // common escapes
      Arguments.of("\\b", '\b'),
      Arguments.of("\\t", '\t'),
      Arguments.of("\\n", '\n'),
      Arguments.of("\\f", '\f'),
      Arguments.of("\\r", '\r'),
      Arguments.of("\"", '"'),
      Arguments.of("\\'", '\''),
      Arguments.of("\\\\", '\\'),
      // octal escapes
      Arguments.of("\\u0000", '\0'),
      Arguments.of("\\u0007", '\7'),
      Arguments.of("?", '\77'),
      Arguments.of("\\u007f", '\177'),
      Arguments.of("¿", '\277'),
      Arguments.of("ÿ", '\377'),
      // unicode escapes
      Arguments.of("\\u0000", '\u0000'),
      Arguments.of("\\u0001", '\u0001'),
      Arguments.of("\\u0002", '\u0002'),
      Arguments.of("€", '\u20AC'),
      Arguments.of("☃", '\u2603'),
      Arguments.of("♠", '\u2660'),
      Arguments.of("♣", '\u2663'),
      Arguments.of("♥", '\u2665'),
      Arguments.of("♦", '\u2666'),
      Arguments.of("✵", '\u2735'),
      Arguments.of("✺", '\u273A'),
      Arguments.of("／", '\uFF0F')
    );
  }

  @Test public void stringLiteral() {
    stringLiteral("abc");
    stringLiteral("♦♥♠♣");
    stringLiteral("€\\t@\\t$", "€\t@\t$", " ");
    stringLiteral("abc();\\n\"\n  + \"def();", "abc();\ndef();", " ");
    stringLiteral("This is \\\"quoted\\\"!", "This is \"quoted\"!", " ");
    stringLiteral("e^{i\\\\pi}+1=0", "e^{i\\pi}+1=0", " ");
  }

  void stringLiteral(String string) {
    stringLiteral(string, string, " ");
  }

  void stringLiteral(String expected, String value, String indent) {
    assertThat("\"" + expected + "\"")
      .isEqualTo(Util.stringLiteralWithDoubleQuotes(value, indent));
  }
}
