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
import dev.architectury.transformer.input.OpenedInputInterface;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class SuggestMappingsTask extends AbstractTask {
    private final RegularFileProperty minecraftJar;
    private final DirectoryProperty mappingsDir;
    
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
    
    @TaskAction
    public void invoke() throws Throwable {
        Path output = getMappingsDir().get().getAsFile().toPath();
        try (OpenedInputInterface inputInterface = OpenedInputInterface.ofJar(getMinecraftJar().get().getAsFile().toPath())) {
            ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            List<CompletableFuture<List<SuggestEntry>>> futures = new ArrayList<>();
            inputInterface.handle((file, bytes) -> {
                if (file.endsWith(".class")) {
                    String[] split = file.split("\\$");
                    for (int i = 1; i < split.length; i++) {
                        if (split[i].isEmpty() || Character.isDigit(split[i].charAt(0))) {
                            return;
                        }
                    }
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        List<SuggestEntry> entries = new ArrayList<>();
                        ClassReader reader = new ClassReader(bytes);
                        if ((reader.getAccess() & Opcodes.ACC_MODULE) == 0) {
                            ClassNode node = new ClassNode(Opcodes.ASM8);
                            reader.accept(node, ClassReader.EXPAND_FRAMES);
                            handleClass(entries, node);
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
            for (Map.Entry<String, List<SuggestEntry>> entry : entries.stream().collect(Collectors.groupingBy(SuggestEntry::methodOwner)).entrySet()) {
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
                    
                    LocalVariableEntry localVariableEntry = new LocalVariableEntry(methodEntry, suggestEntry.lvIndex, suggestEntry.fieldName, true, null);
                    tree.insert(localVariableEntry, new EntryMapping(suggestEntry.fieldName));
                    suggested++;
                }
                if (path.getParent() != null) Files.createDirectories(path.getParent());
                if (!Files.exists(path)) {
                    Files.createFile(path);
                }
                EnigmaMappingsWriter.FILE.write(tree, path, ProgressListener.none(), new MappingSaveParameters(MappingFileNameFormat.BY_OBF));
            }
            logger.lifecycle("Suggested " + entries.size() + " parameters!");
        }
    }
    
    private void handleClass(List<SuggestEntry> entries, ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (method.name.contains("lambda") || (method.access & Opcodes.ACC_SYNTHETIC) != 0) continue;
            int args = Type.getMethodType(method.desc).getArgumentTypes().length;
            StreamSupport.stream(method.instructions.spliterator(), false)
                    .filter(node -> node.getOpcode() == Opcodes.PUTFIELD || node.getOpcode() == Opcodes.PUTSTATIC)
                    .forEach(node -> {
                        AbstractInsnNode previous = getPreviousSkipping(node);
                        AbstractInsnNode previousPrevious = getPreviousSkipping(previous);
                        if (previous != null && previous.getOpcode() >= Opcodes.ILOAD && previous.getOpcode() <= Opcodes.ALOAD
                            && (node.getOpcode() == Opcodes.PUTSTATIC || (previousPrevious != null && previousPrevious.getOpcode() >= Opcodes.ILOAD && previousPrevious.getOpcode() <= Opcodes.ALOAD))) {
                            int targetIndex = ((VarInsnNode) previous).var;
                            if (targetIndex >= ((method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1) + args) return;
                            FieldInsnNode fieldInsnNode = (FieldInsnNode) node;
                            if (fieldInsnNode.name.contains("$")) return;
                            entries.add(new SuggestEntry(fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc,
                                    classNode.name, method.name, method.desc, targetIndex));
                        }
                    });
        }
    }
    
    private record SuggestEntry(
            String fieldOwner,
            String fieldName,
            String fieldDescriptor,
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
