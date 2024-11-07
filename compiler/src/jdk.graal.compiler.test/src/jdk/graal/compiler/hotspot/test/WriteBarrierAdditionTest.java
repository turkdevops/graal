/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import static jdk.graal.compiler.core.common.GraalOptions.AssemblyGCBarriers;
import static jdk.graal.compiler.core.common.GraalOptions.FullUnroll;
import static jdk.graal.compiler.core.common.GraalOptions.LoopPeeling;
import static jdk.graal.compiler.core.common.GraalOptions.OptReadElimination;
import static jdk.graal.compiler.core.common.GraalOptions.PartialEscapeAnalysis;
import static jdk.graal.compiler.core.common.GraalOptions.PartialUnroll;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.referentOffset;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.ListIterator;
import java.util.Objects;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.test.TestPhase;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime.HotSpotGC;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.gc.G1PostWriteBarrierNode;
import jdk.graal.compiler.nodes.gc.G1PreWriteBarrierNode;
import jdk.graal.compiler.nodes.gc.G1ReferentFieldReadBarrierNode;
import jdk.graal.compiler.nodes.gc.SerialWriteBarrierNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.WriteBarrierAdditionPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * The following unit tests assert the presence of write barriers for G1 and for the other GCs that
 * use a simple card mark barrier, like Serial, CMS, ParallelGC and Pthe arNew/ParOld GCs. Normally,
 * the tests check for compile time inserted barriers. However, there are the cases of unsafe loads
 * of the java.lang.ref.Reference.referent field where runtime checks have to be performed also. For
 * those cases, the unit tests check the presence of the compile-time inserted barriers. Concerning
 * the runtime checks, the results of variable inputs (object types and offsets) passed as input
 * parameters can be checked against printed output from the G1 write barrier snippets. The runtime
 * checks have been validated offline.
 */
@SuppressWarnings("this-escape")
public class WriteBarrierAdditionTest extends HotSpotGraalCompilerTest {

    /**
     * The set of GCs known at the time of writing of this test. The number of expected barrier
     * might need to be adjusted for new GCs implementations.
     */
    private static EnumSet<HotSpotGC> knownSupport = EnumSet.of(HotSpotGC.G1, HotSpotGC.Parallel, HotSpotGC.Serial);

    private final GraalHotSpotVMConfig config = runtime().getVMConfig();

    public static class Container {

        public Container a;
        public Container b;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Container container = (Container) o;
            return Objects.equals(a, container.a) && Objects.equals(b, container.b);
        }

        @Override
        public int hashCode() {
            return Objects.hash(a, b);
        }
    }

    @Before
    public void checkAssemblyBarriers() {
        Assume.assumeFalse("doesn't work with assembly barriers ", AssemblyGCBarriers.getValue(getInitialOptions()));
    }

    private int expectedBarriers;

    /**
     * Expected 2 barriers for the card mark GCs and 4 for G1 (2 pre + 2 post).
     */
    @Test
    public void testAllocation() throws Exception {
        this.expectedBarriers = (config.useG1GC()) ? 4 : 2;
        testWithoutPEA("testAllocationSnippet");
    }

    public static Container testAllocationSnippet() {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        main.a = temp1;
        main.b = temp2;
        return main;
    }

    /**
     * Expected 4 barriers for the card mark GCs and 8 for G1 (4 pre + 4 post).
     */
    @Test
    public void testLoopAllocation1() throws Exception {
        this.expectedBarriers = config.useG1GC() ? 8 : 4;
        testWithoutPEA("test2Snippet", false);
        testWithoutPEA("test2Snippet", true);
    }

    public static void test2Snippet(boolean test) {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        for (int i = 0; i < 10; i++) {
            if (test) {
                main.a = temp1;
                main.b = temp2;
            } else {
                main.a = temp2;
                main.b = temp1;
            }
        }
    }

    /**
     * Expected 4 barriers for the card mark GCs and 8 for G1 (4 pre + 4 post).
     */
    @Test
    public void testLoopAllocation2() throws Exception {
        this.expectedBarriers = config.useG1GC() ? 8 : 4;
        testWithoutPEA("test3Snippet");
    }

    public static void test3Snippet() {
        Container[] main = new Container[10];
        Container temp1 = new Container();
        Container temp2 = new Container();
        for (int i = 0; i < 10; i++) {
            main[i].a = main[i].b = temp1;
        }

        for (int i = 0; i < 10; i++) {
            main[i].a = main[i].b = temp2;
        }
    }

    /**
     * Expected 2 barriers for the card mark GCs and 5 for G1 (3 pre + 2 post) The (2 or 4) barriers
     * are emitted while initializing the fields of the WeakReference instance. The extra pre
     * barrier of G1 concerns the read of the referent field.
     */
    @Test
    public void testReferenceGet() throws Exception {
        this.expectedBarriers = config.useG1GC() ? 1 : 0;
        test("testReferenceGetSnippet");
    }

    public static Object testReferenceGetSnippet() {
        return weakReference.get();
    }

    static class DummyReference {
        Object referent;
    }

    private static MetaAccessProvider getStaticMetaAccess() {
        return ((HotSpotBackend) Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend()).getRuntime().getHostProviders().getMetaAccess();
    }

    private static final WeakReference<?> weakReference = new WeakReference<>(new Object());
    private static final Object weakReferenceAsObject = new WeakReference<>(new Object());
    private static final long referenceReferentFieldOffset = HotSpotReplacementsUtil.getFieldOffset(getStaticMetaAccess().lookupJavaType(Reference.class), "referent");
    private static final long referenceQueueFieldOffset = HotSpotReplacementsUtil.getFieldOffset(getStaticMetaAccess().lookupJavaType(Reference.class), "queue");

    private static final DummyReference dummyReference = new DummyReference();
    private static final long dummyReferenceReferentFieldOffset = HotSpotReplacementsUtil.getFieldOffset(getStaticMetaAccess().lookupJavaType(DummyReference.class), "referent");

    /**
     * The type is known to be WeakReference and the offset is a constant, so the
     * {@link RawLoadNode} is converted back into a normal LoadFieldNode and the lowering of the
     * field node inserts the proper barrier.
     */
    @Test
    public void testReferenceReferent1() throws Exception {
        this.expectedBarriers = config.useG1GC() ? 1 : 0;
        test("testReferenceReferentSnippet");
    }

    public Object testReferenceReferentSnippet() {
        return UNSAFE.getReference(weakReference, referenceReferentFieldOffset);
    }

    /**
     * The type is known to be WeakReference and the offset is non-constant, so the lowering of the
     * {@link RawLoadNode} doesn't require a barrier even if it were the referent field. See
     * JDK-8189871 for a discussion.
     */
    @Test
    public void testReferenceReferent2() throws Exception {
        this.expectedBarriers = 0;
        test("testReferenceReferent2Snippet", referenceReferentFieldOffset);
    }

    public Object testReferenceReferent2Snippet(long offset) {
        return UNSAFE.getReference(weakReference, offset);
    }

    /**
     * The type is known to be WeakReference and the offset is constant but not the referent field,
     * so no barrier is required.
     */
    @Test
    public void testReferenceReferent3() throws Exception {
        this.expectedBarriers = 0;
        test("testReferenceReferent3Snippet");
    }

    public Object testReferenceReferent3Snippet() {
        return UNSAFE.getReference(weakReference, referenceQueueFieldOffset);
    }

    /**
     * The type is a super class of WeakReference and the offset is non-constant, so
     * canonicalization of the {@link RawLoadNode} will raise this to a {@link LoadFieldNode} which
     * will have a barrier when the node is lowered.
     */
    @Test
    public void testReferenceReferent4() throws Exception {
        this.expectedBarriers = config.useG1GC() ? 1 : 0;
        test("testReferenceReferent4Snippet");
    }

    public Object testReferenceReferent4Snippet() {
        return UNSAFE.getReference(weakReferenceAsObject, referenceReferentFieldOffset);
    }

    /**
     * The type is not related to Reference at all so no barrier check is required. This should be
     * statically detectable.
     */
    @Test
    public void testReferenceReferent5() throws Exception {
        this.expectedBarriers = 0;
        Assert.assertEquals("expected fields to have the same offset", referenceReferentFieldOffset, dummyReferenceReferentFieldOffset);
        test("testReferenceReferent5Snippet");
    }

    public Object testReferenceReferent5Snippet() {
        return UNSAFE.getReference(dummyReference, referenceReferentFieldOffset);
    }

    static Object[] src = new Object[1];
    static Object[] dst = new Object[1];

    static {
        for (int i = 0; i < src.length; i++) {
            src[i] = new Object();
        }
        for (int i = 0; i < dst.length; i++) {
            dst[i] = new Object();
        }
    }

    public static void testArrayCopySnippet(Object a, Object b, Object c) throws Exception {
        System.arraycopy(a, 0, b, 0, (int) c);
    }

    @Test
    public void testArrayCopy() throws Exception {
        this.expectedBarriers = 0;
        test("testArrayCopySnippet", src, dst, dst.length);
    }

    private void verifyBarriers(StructuredGraph graph) {
        Assert.assertTrue("Unknown collector selected", knownSupport.contains(runtime().getGarbageCollector()));
        Assert.assertNotEquals("test must set expected barrier count", expectedBarriers, -1);
        int barriers = 0;
        if (config.useG1GC()) {
            barriers = graph.getNodes().filter(G1ReferentFieldReadBarrierNode.class).count() + graph.getNodes().filter(G1PreWriteBarrierNode.class).count() +
                            graph.getNodes().filter(G1PostWriteBarrierNode.class).count();
        } else {
            barriers = graph.getNodes().filter(SerialWriteBarrierNode.class).count();
        }
        if (expectedBarriers != barriers) {
            Assert.assertEquals(expectedBarriers, barriers);
        }
        for (WriteNode write : graph.getNodes().filter(WriteNode.class)) {
            if (config.useG1GC()) {
                if (write.getBarrierType() != BarrierType.NONE) {
                    Assert.assertEquals(1, write.successors().count());
                    Assert.assertTrue(write.next() instanceof G1PostWriteBarrierNode);
                    Assert.assertTrue(write.predecessor() instanceof G1PreWriteBarrierNode || write.getLocationIdentity().isImmutable());
                }
            } else {
                if (write.getBarrierType() != BarrierType.NONE) {
                    Assert.assertEquals(1, write.successors().count());
                    Assert.assertTrue(write.next() instanceof SerialWriteBarrierNode);
                }
            }
        }

        for (ReadNode read : graph.getNodes().filter(ReadNode.class)) {
            if (read.getBarrierType() != BarrierType.NONE) {
                if (read.getAddress() instanceof OffsetAddressNode) {
                    JavaConstant constDisp = ((OffsetAddressNode) read.getAddress()).getOffset().asJavaConstant();
                    if (constDisp != null) {
                        Assert.assertEquals(referentOffset(getMetaAccess()), constDisp.asLong());
                    }
                }
                Assert.assertTrue(BarrierType.REFERENCE_GET == read.getBarrierType());
                if (config.useG1GC()) {
                    Assert.assertTrue(read.next() instanceof G1ReferentFieldReadBarrierNode);
                }
            }
        }
    }

    protected Result testWithoutPEA(String name, Object... args) {
        return test(new OptionValues(getInitialOptions(), PartialEscapeAnalysis, false, FullUnroll, false, LoopPeeling, false, PartialUnroll, false, OptReadElimination, false), name, args);
    }

    @Before
    public void before() {
        expectedBarriers = -1;
    }

    /*
     * Check the state of the barriers immediately after insertion.
     */
    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites ret = super.createSuites(opts);
        ListIterator<BasePhase<? super MidTierContext>> iter = ret.getMidTier().findPhase(WriteBarrierAdditionPhase.class, true);
        iter.add(new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                verifyBarriers(graph);
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            public CharSequence getName() {
                return "VerifyBarriersPhase";
            }
        });
        return ret;
    }
}
