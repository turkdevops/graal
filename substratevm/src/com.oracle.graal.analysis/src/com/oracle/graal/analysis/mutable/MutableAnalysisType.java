package com.oracle.graal.analysis.mutable;

import com.oracle.graal.analysis.domain.AnalysisType;

import java.util.Collection;

public interface MutableAnalysisType extends AnalysisType {
    void ensureInitialized();

    void setSubtypes(Collection<AnalysisType> subtypes);
}
