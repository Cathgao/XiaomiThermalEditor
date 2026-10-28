package com.xiaomi.thermaleditor;

import java.util.ArrayList;
import java.util.List;

/**
 * 完整温控配置文件的数据模型
 */
public class ThermalFile {
    private String fileName;
    private ThermalCipher.KeyType keyType = ThermalCipher.KeyType.OLD;
    private String lineSeparator = "\n";
    private final List<ThermalSection> sections = new ArrayList<>();
    private final List<String> headerComments = new ArrayList<>();

    public ThermalFile(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public ThermalCipher.KeyType getKeyType() {
        return keyType;
    }

    public void setKeyType(ThermalCipher.KeyType keyType) {
        this.keyType = keyType;
    }

    public String getLineSeparator() {
        return lineSeparator;
    }

    public void setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    public List<ThermalSection> getSections() {
        return sections;
    }

    public void addSection(ThermalSection section) {
        sections.add(section);
    }

    public List<String> getHeaderComments() {
        return headerComments;
    }

    public ThermalSection findSection(String name) {
        for (ThermalSection s : sections) {
            if (s.getName().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }
}

