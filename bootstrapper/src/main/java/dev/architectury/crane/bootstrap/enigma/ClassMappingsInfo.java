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

package dev.architectury.crane.bootstrap.enigma;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodDefEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.Pair;

import java.util.*;

public class ClassMappingsInfo {
    private static final WeakHashMap<EnigmaProject, ClassMappingsInfo> INFO = new WeakHashMap<>();
    private final Map<String, Double> mapped = new HashMap<>();
    private final Map<String, List<LocalVariableEntry>> counts = new HashMap<>();
    private EnigmaProject project;
    
    public ClassMappingsInfo(EnigmaProject project) {
        Map<String, Boolean> ENUM_MAP = new HashMap<>();
        this.project = project;
        EntryIndex entryIndex = project.getJarIndex().getEntryIndex();
        for (MethodEntry method : entryIndex.getMethods()) {
            ClassEntry parent = method.getParent();
            boolean parentEnum = ENUM_MAP.computeIfAbsent(parent.getFullName(), name ->
                    project.getJarIndex().getEntryIndex().getClassAccess(parent).isEnum());
            List<LocalVariableEntry> entries = counts.computeIfAbsent(parent.getOutermostClass().getFullName(), name -> new ArrayList<>());
            if (!((MethodDefEntry) method).getAccess().isSynthetic()) {
                // Enums pass their names and ordinals to the constructor, we never map those
                int toSkip = parentEnum && method.getName().equals("<init>") ? 2 : 0;
                int index = ((MethodDefEntry) method).getAccess().isStatic() ? 0 : 1;
                for (TypeDescriptor argument : method.getDesc().getArgumentDescs()) {
                    int i = index;
                    index += argument.getSize();
                    if (toSkip-- > 0) continue;
                    entries.add(new LocalVariableEntry(method, i, "", true, null));
                }
            }
        }
    }
    
    public static ClassMappingsInfo get(EnigmaProject project) {
        return INFO.computeIfAbsent(project, ClassMappingsInfo::new);
    }
    
    public double get(ClassEntry entry) {
        Pair<Integer, Integer> inner = getInner(entry);
        if (inner == null || inner.b == 0) return 1;
        return 1 - inner.a / (double) inner.b;
    }
    
    public Pair<Integer, Integer> getInner(ClassEntry entry) {
        List<LocalVariableEntry> entries = counts.get(entry.getFullName());
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        int obfuscated = 0;
        for (LocalVariableEntry variableEntry : entries) {
            if (project.isObfuscated(variableEntry)) {
                obfuscated++;
            }
        }
        return new Pair<>(obfuscated, entries.size());
    }
}
