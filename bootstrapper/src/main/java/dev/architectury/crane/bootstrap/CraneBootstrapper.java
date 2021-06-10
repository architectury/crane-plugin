/*
 * This file is part of architectury.
 * Copyright (C) 2021 architectury
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package dev.architectury.crane.bootstrap;

import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

public class CraneBootstrapper {
    public static void main(String[] args) throws Throwable {
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            BootstrapperPropertyService.PROPERTIES.put((String) entry.getKey(), entry.getValue());
        }
        BootstrapperClassLoader classLoader = new BootstrapperClassLoader(Thread.currentThread().getContextClassLoader());
        Mixins.addConfiguration("crane.mixins.json");
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            Method m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            m.setAccessible(true);
            m.invoke(null, MixinEnvironment.Phase.INIT);
            m.invoke(null, MixinEnvironment.Phase.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        MethodHandles.lookup().findStatic(classLoader.loadClass(args[0]), "main", MethodType.methodType(Void.TYPE, String[].class))
                .invoke((Object) Arrays.stream(args).skip(1).toArray(String[]::new));
    }
}
