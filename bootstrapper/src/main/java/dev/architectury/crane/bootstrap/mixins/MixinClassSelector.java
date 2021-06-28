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

import cuchaz.enigma.gui.ClassSelector;
import cuchaz.enigma.gui.GuiController;
import cuchaz.enigma.gui.node.ClassSelectorClassNode;
import cuchaz.enigma.gui.node.ClassSelectorPackageNode;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.utils.Pair;
import dev.architectury.crane.bootstrap.enigma.ClassMappingsInfo;
import dev.architectury.crane.bootstrap.enigma.ClassSelectorAccessor;
import dev.architectury.crane.bootstrap.enigma.ClassSelectorNodeSuffixAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.Collection;
import java.util.Comparator;

@Mixin(ClassSelector.class)
public class MixinClassSelector extends JTree implements ClassSelectorAccessor {
    @Shadow @Final private GuiController controller;
    
    @Shadow private DefaultMutableTreeNode rootNodes;
    
    @Shadow private Comparator<ClassEntry> comparator;
    
    @Inject(method = "insertNode", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void insertNode(ClassEntry obfEntry, CallbackInfo ci, ClassEntry deobfEntry,
            ClassSelectorPackageNode packageNode, DefaultTreeModel model, ClassSelectorClassNode classNode) {
        if (controller.project == null) return;
        ClassMappingsInfo info = ClassMappingsInfo.get(controller.project);
        double mapped = info.get(classNode.getClassEntry());
        ((ClassSelectorNodeSuffixAccessor) classNode).setSuffix(String.format(" %.1f%%", mapped * 100));
        recalculatePackage(info, packageNode);
        ((DefaultTreeModel) getModel()).nodeChanged(classNode);
    }
    
    @Inject(method = "setClasses", at = @At("RETURN"))
    private void setClasses(Collection<ClassEntry> classEntries, CallbackInfo ci) {
        update();
    }
    
    @Override
    public void update() {
        if (controller.project == null) return;
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        ClassMappingsInfo info = ClassMappingsInfo.get(controller.project);
        for (TreeNode node : (Iterable<TreeNode>) rootNodes.children()::asIterator) {
            if (node instanceof ClassSelectorPackageNode packageNode) {
                for (TreeNode childNode : (Iterable<TreeNode>) packageNode.children()::asIterator) {
                    if (childNode instanceof ClassSelectorClassNode classNode) {
                        double mapped = info.get(classNode.getClassEntry());
                        ((ClassSelectorNodeSuffixAccessor) classNode).setSuffix(String.format(" %.1f%%", mapped * 100));
                        model.nodeChanged(childNode);
                    }
                }
                recalculatePackage(info, packageNode);
            }
        }
    }
    
    @Override
    public void recalculatePackage(ClassMappingsInfo info, ClassSelectorPackageNode node) {
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        int obfuscated = 0;
        int size = 0;
        for (TreeNode childNode : (Iterable<TreeNode>) node.children()::asIterator) {
            if (childNode instanceof ClassSelectorClassNode classNode) {
                Pair<Integer, Integer> inner = info.getInner(classNode.getClassEntry());
                if (inner != null) {
                    obfuscated += inner.a;
                    size += inner.b;
                }
            }
        }
        double percentage = size == 0 ? 1 : 1 - obfuscated / (double) size;
        ((ClassSelectorNodeSuffixAccessor) node).setSuffix(String.format(" %.1f%%", percentage * 100));
        model.nodeChanged(node);
    }
    
    @Override
    public void recalculateClass(ClassMappingsInfo info, ClassSelectorClassNode node) {
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        double mapped = info.get(node.getClassEntry());
        ((ClassSelectorNodeSuffixAccessor) node).setSuffix(String.format(" %.1f%%", mapped * 100));
        model.nodeChanged(node);
    }
}
