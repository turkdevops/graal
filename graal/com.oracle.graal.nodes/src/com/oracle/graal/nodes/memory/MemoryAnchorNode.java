/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes.memory;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeCycles;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodeinfo.NodeSize;
import com.oracle.graal.nodeinfo.StructuralInput.Memory;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(allowedUsageTypes = {InputType.Memory}, cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
public final class MemoryAnchorNode extends FixedWithNextNode implements LIRLowerable, MemoryNode, Canonicalizable {

    public static final NodeClass<MemoryAnchorNode> TYPE = NodeClass.create(MemoryAnchorNode.class);

    public MemoryAnchorNode() {
        super(TYPE, StampFactory.forVoid());
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // Nothing to emit, since this node is used for structural purposes only.
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        return tool.allUsagesAvailable() && hasNoUsages() ? null : this;
    }

    @NodeIntrinsic
    public static native Memory anchor();
}
