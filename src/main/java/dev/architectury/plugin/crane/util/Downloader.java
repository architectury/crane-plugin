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

package dev.architectury.plugin.crane.util;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Downloader {
    public static void downloadTo(Project project, URL url, String expectedHash, Path path) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Path hashLocation = path.getParent().resolve(path.getFileName().toString() + ".hash");
        boolean refreshDependencies = project.getGradle().getStartParameter().isRefreshDependencies();
        boolean matchesHash = !refreshDependencies && Files.exists(hashLocation);
        if (matchesHash) {
            matchesHash = expectedHash.equals(Files.readString(hashLocation, StandardCharsets.UTF_8));
        }
        
        if (!matchesHash) {
            FileUtils.copyURLToFile(url, path.toFile());
            Files.writeString(hashLocation, expectedHash, StandardOpenOption.CREATE);
        }
    }
}
