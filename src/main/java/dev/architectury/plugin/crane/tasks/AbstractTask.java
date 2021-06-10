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

package dev.architectury.plugin.crane.tasks;

import com.google.common.base.Suppliers;
import dev.architectury.plugin.crane.extensions.CraneExtension;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Internal;

import java.util.function.Supplier;

public abstract class AbstractTask extends DefaultTask {
    @Internal
    private final Supplier<CraneExtension> extension = Suppliers.memoize(() -> getProject().getExtensions().getByType(CraneExtension.class));
    
    public CraneExtension getExtension() {
        return extension.get();
    }
    
    public AbstractTask() {
        setGroup("architectury");
    }
}
