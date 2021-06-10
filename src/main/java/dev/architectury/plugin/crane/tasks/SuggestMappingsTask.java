package dev.architectury.plugin.crane.tasks;

import dev.architectury.transformer.input.OpenedInputInterface;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;
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
        try (OpenedInputInterface inputInterface = OpenedInputInterface.ofJar(getMinecraftJar().get().getAsFile().toPath())) {
            ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            List<CompletableFuture<List<SuggestEntry>>> futures = new ArrayList<>();
            inputInterface.handle((file, bytes) -> {
                if (file.endsWith(".class")) {
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
            List<SuggestEntry> entries = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .exceptionally(throwable -> {
                        throw new RuntimeException(throwable);
                    })
                    .thenApply(unused -> futures.stream().flatMap(future -> future.join().stream()).collect(Collectors.toList()))
                    .get();
            for (SuggestEntry entry : entries) {
                System.out.println(entry);
            }
        }
    }
    
    private void handleClass(List<SuggestEntry> entries, ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            StreamSupport.stream(method.instructions.spliterator(), false)
                    .filter(node -> node.getOpcode() == Opcodes.PUTFIELD || node.getOpcode() == Opcodes.PUTSTATIC)
                    .forEach(node -> {
                        AbstractInsnNode previous = getPreviousSkipping(node);
                        AbstractInsnNode previousPrevious = getPreviousSkipping(previous);
                        if (previous != null && previous.getOpcode() >= Opcodes.ILOAD && previous.getOpcode() <= Opcodes.ALOAD && (node.getOpcode() == Opcodes.PUTSTATIC ||
                                                 (previousPrevious != null && previousPrevious.getOpcode() >= Opcodes.ILOAD && previousPrevious.getOpcode() <= Opcodes.ALOAD))) {
                            int targetIndex = ((VarInsnNode) previous).var;
                            FieldInsnNode fieldInsnNode = (FieldInsnNode) node;
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
