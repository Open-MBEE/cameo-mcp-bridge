package com.claude.cameo.bridge.util;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Captures actual installed exporter resource bytes. No schema interpretation. */
public final class NativeExporterResources {
    public static Map<String,byte[]> capture() throws Exception {
        var exporter=com.nomagic.magicdraw.emfuml2xmi.v5.EmfUml2XmiPlugin.class;
        Path source=Path.of(exporter.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path home=Files.isDirectory(source)?source:source.getParent();
        return capture(home.resolve("lib/uml2"),home.resolve("lib/emf"));
    }
    public static Map<String,byte[]> capture(Path uml,Path emf) throws Exception {
        Map<String,byte[]> data=new TreeMap<>();
        Path umlJar=single(uml,"uml2-5\\.uml\\.resources-[0-9.]+\\.jar");
        try(ZipFile jar=new ZipFile(umlJar.toFile())) {
            data.put("catalog/UML.metamodel.uml",read(jar,"metamodels/UML.metamodel.uml",4*1024*1024));
            for(String name:List.of("UMLPrimitiveTypes.library.uml","JavaPrimitiveTypes.library.uml"))
                data.put("catalog/"+name,read(jar,"libraries/"+name,1024*1024));
        }
        try(ZipFile jar=new ZipFile(single(emf,"emf-2\\.10\\.ecore-[0-9.]+\\.jar").toFile())) {
            data.put("catalog/Ecore.ecore",read(jar,"model/Ecore.ecore",1024*1024));
        }
        return data;
    }
    private static Path single(Path directory,String pattern) throws Exception {
        try(var files=Files.list(directory)) {
            var matches=files.filter(p->p.getFileName().toString().matches(pattern)).toList();
            if(matches.size()!=1) throw new IllegalArgumentException("Cannot identify unique installed exporter resources");
            return matches.get(0);
        }
    }
    private static byte[] read(ZipFile jar,String name,int limit) throws Exception {
        var entry=jar.getEntry(name);
        if(entry==null || entry.getSize()>limit) throw new IllegalArgumentException("Installed exporter resource missing or oversized");
        try(var input=jar.getInputStream(entry)) {
            byte[] bytes=input.readNBytes(limit+1);
            if(bytes.length>limit) throw new IllegalArgumentException("Exporter resource exceeds bound");
            return bytes;
        }
    }
}
