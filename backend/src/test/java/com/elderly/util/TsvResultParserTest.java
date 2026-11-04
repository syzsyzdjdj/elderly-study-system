package com.elderly.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TsvResultParserTest {
    @TempDir Path dir;
    @Test void emptyOrWrongHeaderIsNotAnEmptySuccessfulResult() throws Exception {
        Path file = dir.resolve("bad.tsv"); Files.writeString(file, "");
        assertThrows(IllegalStateException.class, () -> TsvResultParser.parseFile(file));
        Files.writeString(file, "unexpected\n");
        assertThrows(IllegalStateException.class, () -> TsvResultParser.parseFile(file, Set.of("user_id")));
    }
    @Test void titleQuotesArePreservedAndExtraColumnsRejected() throws Exception {
        Path file = dir.resolve("text.tsv"); Files.writeString(file, "title\tvalue\n\"医保\"\t1\n");
        assertEquals("\"医保\"", TsvResultParser.parseFile(file).get(0).get("title"));
        Files.writeString(file, "title\tvalue\nx\t1\textra\n");
        assertThrows(IllegalStateException.class, () -> TsvResultParser.parseFile(file));
    }
}
