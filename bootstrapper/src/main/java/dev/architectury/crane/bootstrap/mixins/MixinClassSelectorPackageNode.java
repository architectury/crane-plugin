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

import cuchaz.enigma.gui.node.ClassSelectorPackageNode;
import dev.architectury.crane.bootstrap.enigma.ClassSelectorNodeSuffixAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClassSelectorPackageNode.class)
public class MixinClassSelectorPackageNode implements ClassSelectorNodeSuffixAccessor {
    @Unique
    private String suffix = "";
    
    @Inject(method = "toString", at = @At("RETURN"), cancellable = true)
    private void toString(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(cir.getReturnValue() + suffix);
    }
    
    @Override
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }
}
