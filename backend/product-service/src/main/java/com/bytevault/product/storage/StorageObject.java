package com.bytevault.product.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.InputStream;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageObject {
    private String key;
    private String fileName;
    private String contentType;
    private long size;
    private InputStream inputStream;
}
