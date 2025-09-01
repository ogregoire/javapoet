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
import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Test;

public class UtilTest {
  @Test public void characterLiteral() {
    assertThat("a").isEqualTo(Util.characterLiteralWithoutSingleQuotes('a'));
    assertThat("b").isEqualTo(Util.characterLiteralWithoutSingleQuotes('b'));
    assertThat("c").isEqualTo(Util.characterLiteralWithoutSingleQuotes('c'));
    assertThat("%").isEqualTo(Util.characterLiteralWithoutSingleQuotes('%'));
    // common escapes
    assertThat("\\b").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\b'));
    assertThat("\\t").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\t'));
    assertThat("\\n").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\n'));
    assertThat("\\f").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\f'));
    assertThat("\\r").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\r'));
    assertThat("\"").isEqualTo(Util.characterLiteralWithoutSingleQuotes('"'));
    assertThat("\\'").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\''));
    assertThat("\\\\").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\\'));
    // octal escapes
    assertThat("\\u0000").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\0'));
    assertThat("\\u0007").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\7'));
    assertThat("?").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\77'));
    assertThat("\\u007f").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\177'));
    assertThat("¿").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\277'));
    assertThat("ÿ").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\377'));
    // unicode escapes
    assertThat("\\u0000").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\u0000'));
    assertThat("\\u0001").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\u0001'));
    assertThat("\\u0002").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\u0002'));
    assertThat("€").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\u20AC'));
    assertThat("☃").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\u2603'));
    assertThat("♠").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\u2660'));
    assertThat("♣").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\u2663'));
    assertThat("♥").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\u2665'));
    assertThat("♦").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\u2666'));
    assertThat("✵").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\u2735'));
    assertThat("✺").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\u273A'));
    assertThat("／").isEqualTo(Util.characterLiteralWithoutSingleQuotes('\uFF0F'));
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
