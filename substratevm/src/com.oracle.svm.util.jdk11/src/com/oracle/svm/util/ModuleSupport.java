/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.internal.module.Modules;

@Platforms(Platform.HOSTED_ONLY.class)
public final class ModuleSupport {
    private ModuleSupport() {
    }

    public static void openModuleByClass(Class<?> declaringClass, Class<?> accessingClass) {
        Module declaringModule = declaringClass.getModule();
        String packageName = declaringClass.getPackageName();
        if (accessingClass != null ? declaringModule.isOpen(packageName, accessingClass.getModule()) : declaringModule.isOpen(packageName)) {
            return;
        }
        if (accessingClass != null && accessingClass.getModule().isNamed()) {
            Modules.addOpens(declaringModule, packageName, accessingClass.getModule());
        } else {
            Modules.addOpensToAllUnnamed(declaringModule, packageName);
        }
    }

    /**
     * Exports and opens a single package {@code packageName} in the module named {@code moduleName}
     * to all unnamed modules.
     */
    @SuppressWarnings("unused")
    public static void exportAndOpenPackageToClass(String moduleName, String packageName, boolean optional, Class<?> accessingClass) {
        Optional<Module> value = ModuleLayer.boot().findModule(moduleName);
        if (value.isEmpty()) {
            if (!optional) {
                throw new NoSuchElementException(moduleName);
            }
            return;
        }
        Module declaringModule = value.get();
        Module accessingModule = accessingClass == null ? null : accessingClass.getModule();
        if (accessingModule != null && accessingModule.isNamed()) {
            if (!declaringModule.isOpen(packageName, accessingModule)) {
                Modules.addOpens(declaringModule, packageName, accessingModule);
            }
        } else {
            Modules.addOpensToAllUnnamed(declaringModule, packageName);
        }

    }

    /**
     * Exports and opens all packages in the module named {@code name} to all unnamed modules.
     */
    @SuppressWarnings("unused")
    public static void exportAndOpenAllPackagesToUnnamed(String name, boolean optional) {
        Optional<Module> value = ModuleLayer.boot().findModule(name);
        if (value.isEmpty()) {
            if (!optional) {
                throw new NoSuchElementException("No module in boot layer named " + name + ". Available modules: " + ModuleLayer.boot());
            }
            return;
        }
        Module module = value.get();
        Set<String> packages = module.getPackages();
        for (String pkg : packages) {
            Modules.addExportsToAllUnnamed(module, pkg);
            Modules.addOpensToAllUnnamed(module, pkg);
        }
    }

    /**
     * Exports and opens a single package {@code pkg} in the module named {@code name} to all
     * unnamed modules.
     */
    @SuppressWarnings("unused")
    public static void exportAndOpenPackageToUnnamed(String name, String pkg, boolean optional) {
        Optional<Module> value = ModuleLayer.boot().findModule(name);
        if (value.isEmpty()) {
            if (!optional) {
                throw new NoSuchElementException(name);
            }
            return;
        }
        Module module = value.get();
        Modules.addExportsToAllUnnamed(module, pkg);
        Modules.addOpensToAllUnnamed(module, pkg);
    }

    public static String getModuleName(Class<?> clazz) {
        return clazz.getModule().getName();
    }

    /**
     * In the modules of the boot module layer, filters all resources that match the given
     * predicate, and calls the operation on the matched resources. This is a temporary solution
     * until we fully support modules in native-image
     *
     * @param resourceNameFilter predicate applied to all resource names in the module
     * @param operation a function to process matched resources, it receives the name of the
     *            resources as the first argument and an open stream as the second argument
     */
    @SuppressWarnings("unused")
    public static void findResourcesInModules(Predicate<String> resourceNameFilter, BiConsumer<String, InputStream> operation) throws IOException {
        for (ResolvedModule resolvedModule : ModuleLayer.boot().configuration().modules()) {
            ModuleReference modRef = resolvedModule.reference();
            try (ModuleReader moduleReader = modRef.open()) {
                final List<String> resources = moduleReader.list()
                                .filter(resourceNameFilter)
                                .collect(Collectors.toList());

                for (String resName : resources) {
                    Optional<InputStream> content = moduleReader.open(resName);
                    if (content.isEmpty()) {
                        continue;
                    }
                    InputStream is = content.get();
                    operation.accept(resName, is);
                    is.close();
                }
            }
        }
    }
}
