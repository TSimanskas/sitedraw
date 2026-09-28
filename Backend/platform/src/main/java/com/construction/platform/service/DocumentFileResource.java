package com.construction.platform.service;

import java.io.InputStream;

public record DocumentFileResource(
        String filename,
        InputStream inputStream,
        long contentLength
) {
}
