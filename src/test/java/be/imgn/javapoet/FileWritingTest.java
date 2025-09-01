/*
 * Copyright (C) 2014 Square, Inc.
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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class FileWritingTest {
  // Used for testing java.io File behavior.
  @TempDir
  public Path tmp;

  // Used for testing java.nio.file Path behavior.
  private final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
  private final Path fsRoot = fs.getRootDirectories().iterator().next();

  // Used for testing annotation processor Filer behavior.
  private final TestFiler filer = new TestFiler(fs, fsRoot);

  @Test public void pathNotDirectory() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    var javaFile = JavaFile.builder("example", type).build();
    var path = fs.getPath("/foo/bar");
    Files.createDirectories(path.getParent());
    Files.createFile(path);
    assertThatThrownBy(() -> javaFile.writeTo(path))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("path /foo/bar exists but is not a directory.");
  }

  @Test public void fileNotDirectory() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    var javaFile = JavaFile.builder("example", type).build();
    var file = tmp.resolve("foo").resolve("bar");
    Files.createDirectories(file.getParent());
        Files.createFile(file);
    assertThatThrownBy(() -> javaFile.writeTo(file))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("path " + file + " exists but is not a directory.");
  }

  @Test public void pathDefaultPackage() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    JavaFile.builder("", type).build().writeTo(fsRoot);

    var testPath = fsRoot.resolve("Test.java");
    assertThat(testPath).exists();
  }

  @Test public void fileDefaultPackage() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    JavaFile.builder("", type).build().writeTo(tmp);

    var testFile = tmp.resolve("Test.java");
    assertThat(testFile).exists();
  }

  @Test public void filerDefaultPackage() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    JavaFile.builder("", type).build().writeTo(filer);

    var testPath = fsRoot.resolve("Test.java");
    assertThat(testPath).exists();
  }

  @Test public void pathNestedClasses() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    JavaFile.builder("foo", type).build().writeTo(fsRoot);
    JavaFile.builder("foo.bar", type).build().writeTo(fsRoot);
    JavaFile.builder("foo.bar.baz", type).build().writeTo(fsRoot);

    var fooPath = fsRoot.resolve(fs.getPath("foo", "Test.java"));
    var barPath = fsRoot.resolve(fs.getPath("foo", "bar", "Test.java"));
    var bazPath = fsRoot.resolve(fs.getPath("foo", "bar", "baz", "Test.java"));
    assertThat(fooPath).exists();
    assertThat(barPath).exists();
    assertThat(bazPath).exists();
  }

  @Test public void fileNestedClasses() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    JavaFile.builder("foo", type).build().writeTo(tmp);
    JavaFile.builder("foo.bar", type).build().writeTo(tmp);
    JavaFile.builder("foo.bar.baz", type).build().writeTo(tmp);

    assertThat(tmp.resolve("foo/Test.java")).exists();
    assertThat(tmp.resolve("foo/bar/Test.java")).exists();
    assertThat(tmp.resolve("foo/bar/baz/Test.java")).exists();
  }

  @Test public void filerNestedClasses() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    JavaFile.builder("foo", type).build().writeTo(filer);
    JavaFile.builder("foo.bar", type).build().writeTo(filer);
    JavaFile.builder("foo.bar.baz", type).build().writeTo(filer);

    var fooPath = fsRoot.resolve(fs.getPath("foo", "Test.java"));
    var barPath = fsRoot.resolve(fs.getPath("foo", "bar", "Test.java"));
    var bazPath = fsRoot.resolve(fs.getPath("foo", "bar", "baz", "Test.java"));
    assertThat(fooPath).exists();
    assertThat(barPath).exists();
    assertThat(bazPath).exists();
  }

  @Test public void filerPassesOriginatingElements() throws IOException {
    var element1_1 = Mockito.mock(Element.class);
    var test1 = TypeSpec.classBuilder("Test1")
        .addOriginatingElement(element1_1)
        .build();

    var element2_1 = Mockito.mock(Element.class);
    var element2_2 = Mockito.mock(Element.class);
    var test2 = TypeSpec.classBuilder("Test2")
        .addOriginatingElement(element2_1)
        .addOriginatingElement(element2_2)
        .build();

    JavaFile.builder("example", test1).build().writeTo(filer);
    JavaFile.builder("example", test2).build().writeTo(filer);

    var testPath1 = fsRoot.resolve(fs.getPath("example", "Test1.java"));
    assertThat(filer.getOriginatingElements(testPath1)).containsExactly(element1_1);
    var testPath2 = fsRoot.resolve(fs.getPath("example", "Test2.java"));
    assertThat(filer.getOriginatingElements(testPath2)).containsExactly(element2_1, element2_2);
  }

  @Test public void filerClassesWithTabIndent() throws IOException {
    var test = TypeSpec.classBuilder("Test")
        .addField(Date.class, "madeFreshDate")
        .addMethod(MethodSpec.methodBuilder("main")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(String[].class, "args")
            .addCode("$T.out.println($S);\n", System.class, "Hello World!")
            .build())
        .build();
    JavaFile.builder("foo", test).indent("\t").build().writeTo(filer);

    var fooPath = fsRoot.resolve(fs.getPath("foo", "Test.java"));
    assertThat(fooPath).exists();
    var source = new String(Files.readAllBytes(fooPath));

    assertThat(source).isEqualTo("""
            package foo;

            import java.lang.String;
            import java.lang.System;
            import java.util.Date;

            class Test {
            \tDate madeFreshDate;

            \tpublic static void main(String[] args) {
            \t\tSystem.out.println("Hello World!");
            \t}
            }
            """);
  }

  /**
   * This test confirms that JavaPoet ignores the host charset and always uses UTF-8. The host
   * charset is customized with {@code -Dfile.encoding=ISO-8859-1}.
   */
  @Test public void fileIsUtf8() throws IOException {
    var javaFile = JavaFile.builder("foo", TypeSpec.classBuilder("Taco").build())
        .addFileComment("Pi\u00f1ata\u00a1")
        .build();
    javaFile.writeTo(fsRoot);

    var fooPath = fsRoot.resolve(fs.getPath("foo", "Taco.java"));
    assertThat(new String(Files.readAllBytes(fooPath), UTF_8)).isEqualTo("""
            // Pi\u00f1ata\u00a1
            package foo;

            class Taco {
            }
            """);
  }

  @Test public void writeToPathReturnsPath() throws IOException {
    var javaFile = JavaFile.builder("foo", TypeSpec.classBuilder("Taco").build()).build();
    var filePath = javaFile.writeToPath(fsRoot);
    // Cast to avoid ambiguity between assertThat(Path) and assertThat(Iterable<?>)
    assertThat((Iterable<?>) filePath).isEqualTo(fsRoot.resolve(fs.getPath("foo", "Taco.java")));
  }
}
