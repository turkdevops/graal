/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.constantpool;

import java.nio.ByteBuffer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.shared.classfile.ConstantPool;
import com.oracle.truffle.espresso.shared.constantpool.NameAndTypeConstant;
import com.oracle.truffle.espresso.shared.descriptors.Symbol;
import com.oracle.truffle.espresso.shared.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.shared.descriptors.Symbol.Signature;

public final class FailInvokeDynamicConstant implements LinkableInvokeDynamicConstant {
    private final EspressoException failure;

    public FailInvokeDynamicConstant(EspressoException failure) {
        this.failure = failure;
    }

    @Override
    public CallSiteLink link(RuntimeConstantPool pool, ObjectKlass accessingKlass, int thisIndex, Method method, int bci) {
        throw failure;
    }

    @Override
    public Object value() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Use indy.link() rather than Resolved.value()");
    }

    @Override
    public int getBootstrapMethodAttrIndex() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
    }

    @Override
    public Symbol<Name> getName(ConstantPool pool) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
    }

    @Override
    public Symbol<Signature> getSignature(ConstantPool pool) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
    }

    @Override
    public NameAndTypeConstant getNameAndType(ConstantPool pool) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
    }

    @Override
    public void dump(ByteBuffer buf) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
    }

    @Override
    public boolean isSuccess() {
        return false;
    }
}
