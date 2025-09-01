package be.imgn.javapoet;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class CodeWriterTest {

    @Test
    public void emptyLineInJavaDocDosEndings() throws IOException {
        var javadocCodeBlock = CodeBlock.of("A\r\n\r\nB\r\n");
        var out = new StringBuilder();
        new CodeWriter(out).emitJavadoc(javadocCodeBlock);
        assertThat(out)
      .hasToString(
                "/**\n" +
                        " * A\n" +
                        " *\n" +
                        " * B\n" +
                        " */\n");
    }
}