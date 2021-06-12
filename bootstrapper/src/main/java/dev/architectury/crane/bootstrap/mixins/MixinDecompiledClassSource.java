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

package dev.architectury.crane.bootstrap.mixins;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.source.DecompiledClassSource;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DecompiledClassSource.class)
public class MixinDecompiledClassSource {
    @Redirect(method = "remapToken", at = @At(value = "INVOKE", target = "Lcuchaz/enigma/EnigmaProject;isRenamable(Lcuchaz/enigma/analysis/EntryReference;)Z"))
    private boolean redirectIsRenamable(EnigmaProject enigmaProject, EntryReference<Entry<?>, Entry<?>> obfReference) {
        if (!(obfReference.getNameableEntry() instanceof LocalVariableEntry entry && entry.isArgument())) return false;
        return enigmaProject.isRenamable(obfReference);
    }
}
