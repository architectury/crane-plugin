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

import dev.architectury.transformer.input.FileAccess;
import dev.architectury.transformer.input.OpenedFileAccess;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.nio.file.Files;

public class ExportTinyJarTask extends AbstractTask {
    private final RegularFileProperty tinyFile;
    private final RegularFileProperty output;
    
    public ExportTinyJarTask() {
        ObjectFactory objects = getProject().getObjects();
        this.tinyFile = objects.fileProperty();
        this.output = objects.fileProperty();
    }
    
    @InputFile
    public RegularFileProperty getTinyFile() {
        return tinyFile;
    }
    
    @OutputFile
    public RegularFileProperty getOutput() {
        return output;
    }
    
    @TaskAction
    public void export() throws Throwable {
        File output = this.output.getAsFile().get();
        if (output.getParentFile() != null) {
            output.getParentFile().mkdirs();
        }
        output.delete();
        try (FileAccess access = OpenedFileAccess.ofJar(output.toPath())) {
            access.addFile("crane.tiny", Files.readAllBytes(getTinyFile().getAsFile().get().toPath()));
        }
    }
}
