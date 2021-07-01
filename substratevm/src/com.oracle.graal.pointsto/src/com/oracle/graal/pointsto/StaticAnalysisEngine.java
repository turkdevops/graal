/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.pointsto;

import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.util.Timer;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.options.OptionValues;

import java.io.PrintWriter;
import java.util.List;

public interface StaticAnalysisEngine extends ReachabilityAnalysis, HeapScanning {
    HostVM getHostVM();

    UnsupportedFeatures getUnsupportedFeatures();

    void checkUserLimitations();

    OptionValues getOptions();

    HostedProviders getProviders();

    List<DebugHandlersFactory> getDebugHandlerFactories();

    Timer getAnalysisTimer();

    Timer getProcessFeaturesTimer();

    void printTimers();

    void printTimerStatistics(PrintWriter out);

    ConstantReflectionProvider getConstantReflectionProvider();

    SnippetReflectionProvider getSnippetReflectionProvider();

    DebugContext getDebug();
}
