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
package com.oracle.graal.pointsto.mutable;

import com.oracle.graal.analysis.AnalysisPolicy;
import com.oracle.graal.analysis.api.HostVM;
import com.oracle.graal.analysis.infrastructure.SubstitutionProcessor;
import com.oracle.graal.analysis.mutable.MutableAnalysisUniverse;
import com.oracle.graal.pointsto.meta.BaseAnalysisType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.nativeimage.Platform;

public class MutableAnalysisUniverseImpl extends MutableAnalysisUniverse {
    public MutableAnalysisUniverseImpl(HostVM hostVM, JavaKind wordKind, Platform platform, AnalysisPolicy analysisPolicy, SubstitutionProcessor substitutions, MetaAccessProvider originalMetaAccess,
                    SnippetReflectionProvider originalSnippetReflection, SnippetReflectionProvider snippetReflection, DefaultAnalysisFactory defaultAnalysisFactory) {
        super(hostVM, wordKind, platform, analysisPolicy, substitutions, originalMetaAccess, originalSnippetReflection, snippetReflection, defaultAnalysisFactory);
    }

    @Override
    public BaseAnalysisType objectType() {
        return ((BaseAnalysisType) super.objectType());
    }
}
