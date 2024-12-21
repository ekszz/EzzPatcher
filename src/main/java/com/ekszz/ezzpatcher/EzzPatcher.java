package com.ekszz.ezzpatcher;

import sun.misc.Unsafe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EzzPatcher {
    private static volatile String hashLoaded = null;

    private static final Config config = new Config();

    private static final EzzTransformer transformer = new EzzTransformer(config);

    public static void premain(String agentArgs, Instrumentation inst) {
        String configFilePath = "config.yml";
        if (agentArgs != null && !agentArgs.isEmpty()) {
            configFilePath = agentArgs;
        }
        if (reload(configFilePath)) {
            loadTransformer(inst);
        }
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        if (reload(agentArgs)) {
            loadTransformer(inst);
            retransform(inst);
        }
    }

    private static void loadTransformer(Instrumentation inst) {
        if (hashLoaded == null) {
            hashLoaded = getSelfMd5();
            inst.addTransformer(transformer, true);
            Log.info("[+] EzzPatcher load success. Self HASH: {}.", hashLoaded);
        } else {
            Log.info("[*] EzzPatcher is already loaded, try re-patch class ...");
            if (!hashLoaded.equals(getSelfMd5())) {
                Log.warn("[-] The current EzzPatcher in target JVM is inconsistent with the one running now," +
                        " which is dangerous! JVM HASH: {}.", hashLoaded);
            }
        }
    }

    private static String getSelfMd5() {
        File jar = new File(EzzPatcher.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(jar);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                md5.update(buffer, 0, len);
            }
            fis.close();

            byte[] byteArray = md5.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : byteArray) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.error("[-] An error occurred while calculating self MD5.", e);
        }
        return "UNKNOWN";
    }

    private static boolean reload(String path) {
        ConcurrentHashMap<String, List<ClassMethodDefine>> x = null;
        try {
            Map<String, Object> yamlData = config.readFromLocalYAML(path);
            boolean isReload = config.reloadConfig(yamlData);
            printWelcome();
            if (!isReload) {
                Log.debug("[*] Keep config is ON, config will not be refreshed.");
            } else {
                Log.fromString(config.getConfig().get(Config.CONF_KEY_LOG_LEVEL));
            }
            x = config.getClassMethodDefine(yamlData);
        } catch (FileNotFoundException e) {
            printWelcome();
            Log.error("[-] Config yaml not found at {}.", path);
        } catch (Throwable e) {
            printWelcome();
            Log.error("[-] Config yaml file format error.");
            Log.debug(e);
        }
        if (x != null) {
            config.setClassPatchDefine(x);
            return true;
        }
        return false;
    }

    private static synchronized void retransform(Instrumentation inst) {
        for (Class<?> c : inst.getAllLoadedClasses()) {
            String className = c.getName().replace(".", "/");
            if (config.getClassPatchDefine().containsKey(className)) {
                try {
                    Log.debug("[*] Retransform class: {}", c.getName());
                    inst.retransformClasses(c);
                } catch (Exception e) {
                    Log.warn("[-] Failed to retransform class: {}", c.getName());
                    Log.warn(e);
                }
            }
        }
    }

    private static void printWelcome() {
        if ("false".equals(config.getConfig().get(Config.CONF_KEY_NO_LOGO))) {
            System.out.println(Log.cyan("    ______          ____        __       __             \n" +
                    "   / ____/_______  / __ \\____ _/ /______/ /_  ___  _____\n" +
                    "  / __/ /_  /_  / / /_/ / __ `/ __/ ___/ __ \\/ _ \\/ ___/\n" +
                    " / /___  / /_/ /_/ ____/ /_/ / /_/ /__/ / / /  __/ /    \n" +
                    "/_____/ /___/___/_/    \\__,_/\\__/\\___/_/ /_/\\___/_/     \n" +
                    "                                                {v1.2.0}"));
            System.out.println();
        }
    }

    private static void printHelp() {
        printWelcome();
        System.out.println("Usage:");
        System.out.println("    java -jar EzzPatcher.jar pid");
        System.out.println("        Use default local config file ./config.yml");
        System.out.println("    java -jar EzzPatcher.jar pid CONFIG_FILE_PATH");
        System.out.println("        Use local config file CONFIG_FILE_PATH, like /tmp/config.yml");
        System.out.println();
        System.out.println("Useful gadgets:");
        System.out.println("    java -jar EzzPatcher.jar jps");
        System.out.println("        List JVM processes.");
        System.out.println("    java -jar EzzPatcher.jar b64 [FILE_PATH]");
        System.out.println("        Encode a file using base64, ./config.yml is default.");
        System.out.println();
    }

    public static Class<?> getVirtualMachineClass() {
        Class<?> virtualMachineClass = null;
        try {
            virtualMachineClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        } catch (ClassNotFoundException e) {
            String javaHome = System.getenv().get("JAVA_HOME");
            File javaHomeFile = null;
            if (javaHome != null) {
                javaHomeFile = new File(javaHome);
            } else {
                javaHomeFile = new File(System.getProperty("java.home"));
            }
            File toolsJar = new File(javaHomeFile + File.separator + "lib" + File.separator, "tools.jar");
            if (!toolsJar.exists())
                toolsJar = new File(javaHomeFile.getParentFile() + File.separator + "lib" + File.separator, "tools.jar");
            if (!toolsJar.exists())
                toolsJar = new File(javaHomeFile.getParentFile().getParentFile() + File.separator + "lib" + File.separator, "tools.jar");
            if (!toolsJar.exists()) {
                return null;
            }

            try {
                URL[] urls = {toolsJar.toURI().toURL()};
                virtualMachineClass = (new URLClassLoader(urls)).loadClass("com.sun.tools.attach.VirtualMachine");
            } catch (Throwable ex) {
                System.out.println(Log.red("Load tools.jar fail: " + ex.getMessage()));
                ex.printStackTrace();
            }
        }
        return virtualMachineClass;
    }

    public static void disableWarning() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe u = (Unsafe) theUnsafe.get(null);

            Class<?> cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
            Field logger = cls.getDeclaredField("logger");
            u.putObjectVolatile(cls, u.staticFieldOffset(logger), null);
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) {
        if (args == null || args.length != 1 && args.length != 2) {
            printHelp();
            return;
        }

        if ("jps".equalsIgnoreCase(args[0])) {
            // list vm processes
            Class<?> virtualMachineClass = getVirtualMachineClass();
            if (virtualMachineClass == null) {
                System.out.println(Log.red("tools.jar not found."));
                System.out.println(Log.red("Try to add -Xbootclasspath/a:<path-to-jdk>/lib/tools.jar to cmd line."));
                System.out.println(Log.red("Example:\n    java -Xbootclasspath/a:<path-to-jdk>/lib/tools.jar -jar EzzPatcher.jar jps"));
                System.out.println();
            } else {
                try {
                    List<?> list = (List<?>) virtualMachineClass.getDeclaredMethod("list").invoke(null, new Object[0]);
                    for (Object vmd : list) {
                        Class<?> descriptorClass = vmd.getClass();
                        String pid = (String) descriptorClass.getMethod("id").invoke(vmd, new Object[0]);
                        String displayName = (String) descriptorClass.getMethod("displayName").invoke(vmd, new Object[0]);
                        System.out.println(pid + " " + displayName);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if ("b64".equalsIgnoreCase(args[0])) {
            // base64 config file
            String configFilePath = args.length == 1 ? "config.yml" : args[1];
            Path path = Paths.get(configFilePath);
            try {
                byte[] bytes = Files.readAllBytes(path);
                System.out.println(Base64.getEncoder().encodeToString(bytes));
            } catch (NoSuchFileException e) {
                System.out.println("ERROR: File not found: " + path);
            } catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        } else {
            // main branch
            printWelcome();
            String configFilePath = args.length == 1 ? "config.yml" : args[1];

            disableWarning();
            Class<?> virtualMachineClass = getVirtualMachineClass();
            if (virtualMachineClass == null) {
                System.out.println(Log.red("tools.jar not found."));
                System.out.println(Log.red("Try to add -Xbootclasspath/a:<path-to-jdk>/lib/tools.jar to cmd line."));
                System.out.println(Log.red("Example:\n    java -Xbootclasspath/a:<path-to-jdk>/lib/tools.jar -jar EzzPatcher.jar pid CONFIGFILE_PATH"));
                System.out.println();
                return;
            }

            boolean found = false;
            try {
                List<?> list = (List<?>) virtualMachineClass.getDeclaredMethod("list").invoke(null, new Object[0]);
                for (Object vmd : list) {
                    Class<?> descriptorClass = vmd.getClass();
                    String pid = (String) descriptorClass.getMethod("id").invoke(vmd, new Object[0]);
                    String displayName = (String) descriptorClass.getMethod("displayName").invoke(vmd, new Object[0]);
                    if (pid.equals(args[0])) {
                        found = true;
                        try {
                            System.out.println(Log.green("JVM: ") + displayName);
                            Object virtualMachine = virtualMachineClass.getDeclaredMethod("attach", String.class).invoke(null, pid);
                            String path = new File(EzzPatcher.class.getProtectionDomain().getCodeSource().getLocation()
                                    .getPath()).getAbsolutePath();
                            System.out.println(Log.yellow("JAR: ") + path);
                            System.out.println();
                            virtualMachineClass.getDeclaredMethod("loadAgent", String.class, String.class).invoke(virtualMachine, path, configFilePath);
                            virtualMachine.getClass().getDeclaredMethod("detach").invoke(virtualMachine);
                            System.out.println(Log.cyan("Completed."));
                            System.out.println(Log.cyan("Logs will be printed in the target JVM."));
                            System.out.println(Log.cyan("Good luck !!!"));
                            System.out.println();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            if (!found) {
                System.out.println(Log.red("JVM not found. pid = " + args[0] + "."));
                System.out.println();
            }
        }
    }
}