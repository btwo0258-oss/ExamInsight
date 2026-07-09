package com.example.llm.service;

import com.example.llm.entity.Document;

public interface AsyncDocumentService {
    void processDocument(Document doc);
}
