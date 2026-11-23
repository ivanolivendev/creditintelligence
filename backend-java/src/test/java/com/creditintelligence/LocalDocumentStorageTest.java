package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditintelligence.document.infrastructure.LocalDocumentStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDocumentStorageTest {
    @Test
    void storesAndDeletesDocument(@TempDir Path root) throws Exception {
        var storage = new LocalDocumentStorage(root.toString());

        storage.put("proposal/document.pdf", new byte[] {1, 2, 3});
        assertThat(Files.readAllBytes(root.resolve("proposal/document.pdf")))
                .containsExactly(1, 2, 3);

        storage.delete("proposal/document.pdf");
        assertThat(Files.exists(root.resolve("proposal/document.pdf"))).isFalse();
    }

    @Test
    void rejectsPathTraversal() {
        var storage = new LocalDocumentStorage("target/test-documents");

        assertThatThrownBy(() -> storage.put("../outside.pdf", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid storage key");
    }

    @Test
    void deletingMissingDocumentIsIdempotent(@TempDir Path root) {
        var storage = new LocalDocumentStorage(root.toString());

        storage.delete("missing/document.pdf");
    }
}
