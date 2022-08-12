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

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.serde.MappingFileNameFormat;
import cuchaz.enigma.translation.mapping.serde.MappingSaveParameters;
import cuchaz.enigma.translation.mapping.serde.enigma.EnigmaMappingsReader;
import cuchaz.enigma.translation.mapping.serde.enigma.EnigmaMappingsWriter;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import dev.architectury.transformer.input.FileAccess;
import dev.architectury.transformer.input.FileView;
import dev.architectury.transformer.input.OpenedFileAccess;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class SuggestMappingsTask extends AbstractTask {
    private final RegularFileProperty minecraftJar;
    private final DirectoryProperty mappingsDir;
    private Map<String, String> autoMaps = new HashMap<>();
    private Map<String, String> autoPropose = new HashMap<>();
    
    public SuggestMappingsTask() {
        ObjectFactory objects = getProject().getObjects();
        this.minecraftJar = objects.fileProperty();
        this.mappingsDir = objects.directoryProperty();
    }
    
    @InputFile
    public RegularFileProperty getMinecraftJar() {
        return minecraftJar;
    }
    
    @InputDirectory
    public DirectoryProperty getMappingsDir() {
        return mappingsDir;
    }
    
    @Input
    public Map<String, String> getAutoMaps() {
        return autoMaps;
    }
    
    public void autoMap(String className, String name) {
        this.autoMaps.put(className, name);
    }
    
    @Input
    public Map<String, String> getAutoPropose() {
        return autoPropose;
    }
    
    public void autoPropose(String className, String name) {
        this.autoPropose.put(className, name);
    }
    
    @TaskAction
    public void invoke() throws Throwable {
        Path output = getMappingsDir().get().getAsFile().toPath();
        try (FileView view = OpenedFileAccess.ofJar(getMinecraftJar().get().getAsFile().toPath())) {
            ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            List<CompletableFuture<List<SuggestEntry>>> futures = new ArrayList<>();
            view.handle((file, bytes) -> {
                if (file.endsWith(".class")) {
                    String[] split = file.split("\\$");
                    for (int i = 1; i < split.length; i++) {
                        if (split[i].isEmpty() || Character.isDigit(split[i].charAt(0))) {
                            return;
                        }
                    }
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        Set<SuggestDistinctEntry> set = new HashSet<>();
                        List<SuggestEntry> entries = new ArrayList<>();
                        ClassReader reader = new ClassReader(bytes);
                        if ((reader.getAccess() & Opcodes.ACC_MODULE) == 0) {
                            ClassNode node = new ClassNode(Opcodes.ASM8);
                            reader.accept(node, ClassReader.EXPAND_FRAMES);
                            handleClass(entry -> {
                                if (set.add(new SuggestDistinctEntry(entry.methodOwner, entry.methodName, entry.methodDescriptor, entry.lvIndex))) {
                                    entries.add(entry);
                                }
                            }, node);
                        }
                        return entries;
                    }, service));
                }
            });
            Logger logger = getProject().getLogger();
            List<SuggestEntry> entries = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .exceptionally(throwable -> {
                        throw new RuntimeException(throwable);
                    })
                    .thenApply(unused -> futures.stream().flatMap(future -> future.join().stream()).collect(Collectors.toList()))
                    .get();
            int suggested = 0;
            for (Map.Entry<String, List<SuggestEntry>> entry : entries.stream().distinct().collect(Collectors.groupingBy(SuggestEntry::methodOwner)).entrySet()) {
                String className = entry.getKey();
                String[] paths = className.split("\\$");
                Path path = output.resolve(paths[0] + ".mapping");
                EntryTree<EntryMapping> tree;
                if (Files.exists(path)) {
                    tree = EnigmaMappingsReader.readFiles(ProgressListener.none(), path);
                } else {
                    tree = new HashEntryTree<>();
                    ClassEntry lastEntry = null;
                    for (int i = 0; i < paths.length; i++) {
                        if (i == 0) {
                            lastEntry = new ClassEntry(paths[i]);
                        } else {
                            lastEntry = new ClassEntry(lastEntry, paths[i]);
                        }
                    }
                }
                ClassEntry classEntry = null;
                for (int i = 0; i < paths.length; i++) {
                    if (i == 0) {
                        classEntry = new ClassEntry(paths[i]);
                    } else {
                        classEntry = new ClassEntry(classEntry, paths[i]);
                    }
                }
                EntryMapping obj = tree.get(classEntry);
                a:
                for (SuggestEntry suggestEntry : entry.getValue()) {
                    MethodEntry methodEntry = new MethodEntry(classEntry, suggestEntry.methodName, new MethodDescriptor(suggestEntry.methodDescriptor));
                    
                    for (Entry<?> child : tree.getChildren(methodEntry)) {
                        if (child instanceof LocalVariableEntry localVar) {
                            if (localVar.isArgument() && localVar.getIndex() == suggestEntry.lvIndex) continue a;
                        }
                    }
                    
                    LocalVariableEntry localVariableEntry = new LocalVariableEntry(methodEntry, suggestEntry.lvIndex, "", true, null);
                    tree.insert(localVariableEntry, new EntryMapping(suggestEntry.suggestionName));
                    suggested++;
                }
                if (path.getParent() != null) Files.createDirectories(path.getParent());
                if (!Files.exists(path)) {
                    Files.createFile(path);
                }
                EnigmaMappingsWriter.FILE.write(tree, path, ProgressListener.none(), new MappingSaveParameters(MappingFileNameFormat.BY_OBF));
            }
            logger.lifecycle("Suggested " + suggested + " parameters!");
        }
        try (FileAccess access = OpenedFileAccess.ofDirectory(output)) {
            access.deleteFiles((s, bytes) -> {
                if (s.endsWith(".mapping")) {
                    return new String(bytes, StandardCharsets.UTF_8).lines()
                                   .filter(line -> !line.isBlank())
                                   .count() <= 1;
                }
                return true;
            });
        }
    }
    
    private void handleClass(Consumer<SuggestEntry> consumer, ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (method.name.contains("lambda") || (method.access & Opcodes.ACC_SYNTHETIC) != 0) continue;
            boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
            Type methodType = Type.getMethodType(method.desc);
            Type[] argumentTypes = methodType.getArgumentTypes();
            int args = argumentTypes.length;
            StreamSupport.stream(method.instructions.spliterator(), false)
                    .filter(node -> node.getOpcode() == Opcodes.PUTFIELD || node.getOpcode() == Opcodes.PUTSTATIC)
                    .forEach(node -> {
                        AbstractInsnNode previous = getPreviousSkipping(node);
                        AbstractInsnNode previousPrevious = getPreviousSkipping(previous);
                        if (previous != null && previous.getOpcode() >= Opcodes.ILOAD && previous.getOpcode() <= Opcodes.ALOAD
                            && (node.getOpcode() == Opcodes.PUTSTATIC || (previousPrevious != null && previousPrevious.getOpcode() >= Opcodes.ILOAD && previousPrevious.getOpcode() <= Opcodes.ALOAD))) {
                            int targetIndex = ((VarInsnNode) previous).var;
                            if (targetIndex >= (isStatic ? 0 : 1) + args) return;
                            FieldInsnNode fieldInsnNode = (FieldInsnNode) node;
                            if (fieldInsnNode.name.contains("$")) return;
                            consumer.accept(new SuggestEntry(fieldInsnNode.name,
                                    classNode.name, method.name, method.desc, targetIndex));
                        }
                    });
            if (args == 1) {
                Type type = argumentTypes[0];
                String autoMap = autoMaps.get(type.getInternalName());
                if (autoMap != null) {
                    consumer.accept(new SuggestEntry(autoMap,
                            classNode.name, method.name, method.desc, isStatic ? 0 : 1));
                }
            }
            for (Map.Entry<String, String> entry : autoPropose.entrySet()) {
                int lastIndex = -1;
                for (int i = 0; i < argumentTypes.length; i++) {
                    Type argumentType = argumentTypes[i];
                    if (Objects.equals(entry.getKey(), argumentType.getInternalName())) {
                        if (lastIndex == -1) {
                            lastIndex = i;
                        } else {
                            lastIndex = -1;
                            break;
                        }
                    }
                }
                if (lastIndex >= 0) {
                    consumer.accept(new SuggestEntry(entry.getValue(),
                            classNode.name, method.name, method.desc, lastIndex + (isStatic ? 0 : 1)));
                }
            }
        }
    }
    
    private record SuggestEntry(
            String suggestionName,
            String methodOwner,
            String methodName,
            String methodDescriptor,
            int lvIndex
    ) {}
    
    private record SuggestDistinctEntry(
            String methodOwner,
            String methodName,
            String methodDescriptor,
            int lvIndex
    ) {}
    
    private AbstractInsnNode getPreviousSkipping(AbstractInsnNode node) {
        if (node == null) return null;
        AbstractInsnNode previous = node.getPrevious();
        if (previous instanceof LabelNode) {
            return getPreviousSkipping(previous);
        }
        return previous;
    }
}
