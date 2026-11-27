package com.opticareer.app.modules.analysis.application;

public interface AnalysisJobQueue {
    void enqueue(AnalysisJob job);
}
