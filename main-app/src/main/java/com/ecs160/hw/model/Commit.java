package com.ecs160.hw.model;

import java.util.ArrayList;
import java.util.List;

public class Commit {
    private String sha;
    private String message;
    private List<String> modifiedFiles;

    public Commit() {
        this.modifiedFiles = new ArrayList<>();
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getModifiedFiles() {
        return modifiedFiles;
    }

    public void setModifiedFiles(List<String> modifiedFiles) {
        this.modifiedFiles = modifiedFiles;
    }

    public void addModifiedFile(String file) {
        this.modifiedFiles.add(file);
    }
}
