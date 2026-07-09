package com.example.llm.component;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class DocumentParser {
    public String parse(File file) throws Exception {
        Tika tika = new Tika();
        return tika.parseToString(file);
    }
}
