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

import cuchaz.enigma.gui.Gui;
import cuchaz.enigma.gui.elements.MenuBar;
import dev.architectury.crane.bootstrap.enigma.ClassSelectorAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

@Mixin(MenuBar.class)
public class MixinMenuBar {
    @Shadow @Final private JMenu fileMenu;
    @Unique
    private final JMenuItem updatePercentages = new JMenuItem();
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(Gui gui, CallbackInfo ci) {
        fileMenu.addSeparator();
        fileMenu.add(updatePercentages);
        updatePercentages.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> {
                ((ClassSelectorAccessor) gui.getObfPanel().obfClasses).update();
                ((ClassSelectorAccessor) gui.getDeobfPanel().deobfClasses).update();
            });
        });
    }
    
    @Inject(method = "updateUiState", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void updateUiState(CallbackInfo ci, boolean jarOpen) {
        updatePercentages.setEnabled(jarOpen);
    }
    
    @Inject(method = "retranslateUi", at = @At("RETURN"))
    private void retranslateUi(CallbackInfo ci) {
        updatePercentages.setText("Architectury Crane: Update Mapped Stats");
    }
}
