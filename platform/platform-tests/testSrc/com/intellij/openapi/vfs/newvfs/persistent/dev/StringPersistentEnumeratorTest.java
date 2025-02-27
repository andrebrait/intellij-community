// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.util.io.DataEnumeratorEx.NULL_ID;
import static org.junit.Assert.assertEquals;

public class StringPersistentEnumeratorTest extends StringEnumeratorTestBase<StringPersistentEnumerator> {

  @Test
  public void nullValue_EnumeratedTo_NULL_ID() throws IOException {
    int id = enumerator.enumerate(null);
    assertEquals(
      "null value enumerated to NULL_ID",
      NULL_ID,
      id
    );
  }

  @Override
  protected StringPersistentEnumerator openEnumerator(@NotNull Path storagePath) throws IOException {
    return new StringPersistentEnumerator(storagePath);
  }
}