package com.oracle.graal.analysis.api;

import com.oracle.graal.analysis.domain.AnalysisType;
import com.oracle.graal.analysis.domain.AnalysisUniverse;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.options.OptionValues;

public interface HostVM {

    OptionValues options();

    void clearInThread();

    void installInThread(Object vmConfig);

    boolean isRelocatedPointer(Object originalObject);

    void checkType(ResolvedJavaType type, AnalysisUniverse universe);

    void registerType(AnalysisType newValue);

    void initializeType(AnalysisType newValue);

    @SuppressWarnings("unused")
    default boolean skipInterface(AnalysisUniverse universe, ResolvedJavaType interfaceType, ResolvedJavaType implementingType) {
        return false;
    }
}
