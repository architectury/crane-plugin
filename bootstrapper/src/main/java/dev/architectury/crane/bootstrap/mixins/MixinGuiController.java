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

import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.gui.Gui;
import cuchaz.enigma.gui.GuiController;
import cuchaz.enigma.gui.node.ClassSelectorClassNode;
import cuchaz.enigma.gui.node.ClassSelectorPackageNode;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.utils.validation.ValidationContext;
import dev.architectury.crane.bootstrap.enigma.ClassMappingsInfo;
import dev.architectury.crane.bootstrap.enigma.ClassSelectorAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.swing.SwingUtilities;
import javax.swing.tree.TreeNode;

@Mixin(GuiController.class)
public class MixinGuiController {
    @Shadow @Final private Gui gui;
    
    @Inject(method = "refreshClasses", at = @At("RETURN"))
    private void refreshClasses(CallbackInfo ci) {
        SwingUtilities.invokeLater(() -> {
            ((ClassSelectorAccessor) gui.getObfPanel().obfClasses).update();
            ((ClassSelectorAccessor) gui.getDeobfPanel().deobfClasses).update();
        });
    }
    
    @Inject(method = "rename(Lcuchaz/enigma/utils/validation/ValidationContext;Lcuchaz/enigma/analysis/EntryReference;Ljava/lang/String;ZZ)V",
            at = @At("RETURN"))
    private void rename(ValidationContext vc, EntryReference<Entry<?>, Entry<?>> reference, String newName, boolean refreshClassTree, boolean validateOnly, CallbackInfo ci) {
        if (vc.canProceed()) {
            ClassMappingsInfo info = ClassMappingsInfo.get(gui.getController().project);
            Entry<?> entry = reference.entry;
            if (entry == null) return;
            ClassEntry containingClass = entry.getContainingClass();
            if (containingClass == null) return;
            
            ClassSelectorPackageNode packageNode = gui.getObfPanel().obfClasses.getPackageNode(containingClass);
            if (packageNode != null) {
                ((ClassSelectorAccessor) gui.getObfPanel().obfClasses).recalculatePackage(info, packageNode);
                
                for (TreeNode childNode : (Iterable<TreeNode>) packageNode.children()::asIterator) {
                    if (childNode instanceof ClassSelectorClassNode classNode) {
                        if (classNode.getClassEntry().equals(containingClass)) {
                            ((ClassSelectorAccessor) gui.getObfPanel().obfClasses).recalculateClass(info, classNode);
                            break;
                        }
                    }
                }
            }
            
            packageNode = gui.getDeobfPanel().deobfClasses.getPackageNode(containingClass);
            if (packageNode != null) {
                ((ClassSelectorAccessor) gui.getDeobfPanel().deobfClasses).recalculatePackage(info, packageNode);
                
                for (TreeNode childNode : (Iterable<TreeNode>) packageNode.children()::asIterator) {
                    if (childNode instanceof ClassSelectorClassNode classNode) {
                        if (classNode.getClassEntry().equals(containingClass)) {
                            ((ClassSelectorAccessor) gui.getObfPanel().obfClasses).recalculateClass(info, classNode);
                            break;
                        }
                    }
                }
            }
        }
    }
}
