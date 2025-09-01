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

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

import static be.imgn.javapoet.Util.characterLiteralWithoutSingleQuotes;
import static be.imgn.javapoet.Util.checkArgument;
import static be.imgn.javapoet.Util.checkNotNull;

/** A generated annotation on a declaration. */
public final class AnnotationSpec {
  public static final String VALUE = "value";

  public final TypeName type;
  public final Map<String, List<CodeBlock>> members;

  private AnnotationSpec(Builder builder) {
    this.type = builder.type;
    this.members = Util.immutableMultimap(builder.members);
  }

  void emit(CodeWriter codeWriter, boolean inline) throws IOException {
    var whitespace = inline ? "" : "\n";
    var memberSeparator = inline ? ", " : ",\n";
    if (members.isEmpty()) {
      // @Singleton
      codeWriter.emit("@$T", type);
    } else if (members.size() == 1 && members.containsKey(VALUE)) {
      // @Named("foo")
      codeWriter.emit("@$T(", type);
      emitAnnotationValues(codeWriter, whitespace, memberSeparator, members.get(VALUE));
      codeWriter.emit(")");
    } else {
      // Inline:
      //   @Column(name = "updated_at", nullable = false)
      //
      // Not inline:
      //   @Column(
      //       name = "updated_at",
      //       nullable = false
      //   )
      codeWriter.emit("@$T(" + whitespace, type);
      codeWriter.indent(2);
      for (var i = members.entrySet().iterator(); i.hasNext(); ) {
        var entry = i.next();
        codeWriter.emit("$L = ", entry.getKey());
        emitAnnotationValues(codeWriter, whitespace, memberSeparator, entry.getValue());
        if (i.hasNext()) codeWriter.emit(memberSeparator);
      }
      codeWriter.unindent(2);
      codeWriter.emit(whitespace + ")");
    }
  }

  private void emitAnnotationValues(CodeWriter codeWriter, String whitespace,
      String memberSeparator, List<CodeBlock> values) throws IOException {
    if (values.size() == 1) {
      codeWriter.indent(2);
      codeWriter.emit(values.getFirst());
      codeWriter.unindent(2);
      return;
    }

    codeWriter.emit("{" + whitespace);
    codeWriter.indent(2);
    var first = true;
    for (var codeBlock : values) {
      if (!first) codeWriter.emit(memberSeparator);
      codeWriter.emit(codeBlock);
      first = false;
    }
    codeWriter.unindent(2);
    codeWriter.emit(whitespace + "}");
  }

  public static AnnotationSpec get(Annotation annotation) {
    return get(annotation, false);
  }

  public static AnnotationSpec get(Annotation annotation, boolean includeDefaultValues) {
    var builder = builder(annotation.annotationType());
    try {
      var methods = annotation.annotationType().getDeclaredMethods();
      Arrays.sort(methods, Comparator.comparing(Method::getName));
      for (var method : methods) {
        var value = method.invoke(annotation);
        if (!includeDefaultValues) {
          if (Objects.deepEquals(value, method.getDefaultValue())) {
            continue;
          }
        }
        if (value.getClass().isArray()) {
          for (int i = 0; i < Array.getLength(value); i++) {
            builder.addMemberForValue(method.getName(), Array.get(value, i));
          }
          continue;
        }
        if (value instanceof Annotation annotationValue) {
          builder.addMember(method.getName(), "$L", get(annotationValue));
          continue;
        }
        builder.addMemberForValue(method.getName(), value);
      }
    } catch (Exception e) {
      throw new RuntimeException("Reflecting " + annotation + " failed!", e);
    }
    return builder.build();
  }

  public static AnnotationSpec get(AnnotationMirror annotation) {
    var element = (TypeElement) annotation.getAnnotationType().asElement();
    var builder = AnnotationSpec.builder(ClassName.get(element));
    var visitor = new Visitor(builder);
    for (var executableElement : annotation.getElementValues().keySet()) {
      var name = executableElement.getSimpleName().toString();
      var value = annotation.getElementValues().get(executableElement);
      value.accept(visitor, name);
    }
    return builder.build();
  }

  public static Builder builder(ClassName type) {
    checkNotNull(type, "type == null");
    return new Builder(type);
  }

  public static Builder builder(Class<?> type) {
    return builder(ClassName.get(type));
  }

  public Builder toBuilder() {
    var builder = new Builder(type);
    for (var entry : members.entrySet()) {
      builder.members.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    return builder;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (getClass() != o.getClass()) return false;
    return toString().equals(o.toString());
  }

  @Override public int hashCode() {
    return toString().hashCode();
  }

  @Override public String toString() {
    var out = new StringBuilder();
    try {
      var codeWriter = new CodeWriter(out);
      codeWriter.emit("$L", this);
      return out.toString();
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  public static final class Builder {
    private final TypeName type;

    public final Map<String, List<CodeBlock>> members = new LinkedHashMap<>();

    private Builder(TypeName type) {
      this.type = type;
    }

    public Builder addMember(String name, String format, Object... args) {
      return addMember(name, CodeBlock.of(format, args));
    }

    public Builder addMember(String name, CodeBlock codeBlock) {
      var values = members.computeIfAbsent(name, k -> new ArrayList<>());
      values.add(codeBlock);
      return this;
    }

    /**
     * Delegates to {@link #addMember(String, String, Object...)}, with parameter {@code format}
     * depending on the given {@code value} object. Falls back to {@code "$L"} literal format if
     * the class of the given {@code value} object is not supported.
     */
    Builder addMemberForValue(String memberName, Object value) {
      checkNotNull(memberName, "memberName == null");
      checkNotNull(value, "value == null, constant non-null value expected for %s", memberName);
      checkArgument(SourceVersion.isName(memberName), "not a valid name: %s", memberName);
      return switch (value) {
        case Class<?> classValue ->
          addMember(memberName, "$T.class", classValue);
        case Enum<?> enumValue ->
          addMember(memberName, "$T.$L", value.getClass(), enumValue.name());
        case String stringValue ->
          addMember(memberName, "$S", stringValue);
        case Float floatValue ->
          addMember(memberName, "$Lf", floatValue);
        case Long longValue ->
          addMember(memberName, "$LL", longValue);
        case Character characterValue ->
          addMember(memberName, "'$L'", characterLiteralWithoutSingleQuotes(characterValue));
        default ->
          addMember(memberName, "$L", value);
      };
    }

    public AnnotationSpec build() {
      for (var name : members.keySet()) {
        checkNotNull(name, "name == null");
        checkArgument(SourceVersion.isName(name), "not a valid name: %s", name);
      }
      return new AnnotationSpec(this);
    }
  }

  /**
   * Annotation value visitor adding members to the given builder instance.
   */
  private static class Visitor extends SimpleAnnotationValueVisitor8<Builder, String> {
    final Builder builder;

    Visitor(Builder builder) {
      super(builder);
      this.builder = builder;
    }

    @Override protected Builder defaultAction(Object o, String name) {
      return builder.addMemberForValue(name, o);
    }

    @Override public Builder visitAnnotation(AnnotationMirror a, String name) {
      return builder.addMember(name, "$L", get(a));
    }

    @Override public Builder visitEnumConstant(VariableElement c, String name) {
      return builder.addMember(name, "$T.$L", c.asType(), c.getSimpleName());
    }

    @Override public Builder visitType(TypeMirror t, String name) {
      return builder.addMember(name, "$T.class", t);
    }

    @Override public Builder visitArray(List<? extends AnnotationValue> values, String name) {
      for (var value : values) {
        value.accept(this, name);
      }
      return builder;
    }
  }
}
