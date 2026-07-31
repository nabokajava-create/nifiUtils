package com.example.nifi.model;

import java.util.List;
import java.util.Map;

public class ProcessGroupInfo {
    private String id;
    private String name;
    private String status;
    private int processorCount;
    private List<String> processors;

    public ProcessGroupInfo() {}

    public ProcessGroupInfo(String id, String name, String status, int processorCount, List<String> processors) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.processorCount = processorCount;
        this.processors = processors;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProcessorCount() {
        return processorCount;
    }

    public void setProcessorCount(int processorCount) {
        this.processorCount = processorCount;
    }

    public List<String> getProcessors() {
        return processors;
    }

    public void setProcessors(List<String> processors) {
        this.processors = processors;
    }
}
