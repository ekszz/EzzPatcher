package com.ekszz.ezzpatcher;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.MessageDigest;
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
                } catch (UnmodifiableClassException e) {
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
                    "                                                {v1.1.0}"));
            System.out.println();
        }
    }

    private static void printHelp() {
        printWelcome();
        System.out.println("Usage:");
        System.out.println("    java -Xbootclasspath/a:<path-to-jdk>/lib/tools.jar -jar EzzPatcher.jar pid");
        System.out.println("        Use default local config file ./config.yml");
        System.out.println("    java -Xbootclasspath/a:<path-to-jdk>/lib/tools.jar -jar EzzPatcher.jar pid CONFIGFILE_PATH");
        System.out.println("        Use local config file CONFIGFILE_PATH, like /tmp/config.yml");
        System.out.println();
    }

    public static void main(String[] args) {
        if (args == null || args.length != 1 && args.length != 2) {
            printHelp();
            return;
        }

        printWelcome();
        String configFilePath = args.length == 1 ? "config.yml" : args[1];
        List<VirtualMachineDescriptor> list = VirtualMachine.list();
        boolean found = false;
        for (VirtualMachineDescriptor vmd : list) {
            if (vmd.id().equals(args[0])) {
                found = true;
                try {
                    System.out.println(Log.green("JVM: ") + vmd.displayName());
                    VirtualMachine virtualMachine = VirtualMachine.attach(vmd.id());
                    String path = new File(EzzPatcher.class.getProtectionDomain().getCodeSource().getLocation()
                            .getPath()).getAbsolutePath();
                    System.out.println(Log.yellow("JAR: ") + path);
                    System.out.println();
                    virtualMachine.loadAgent(path, configFilePath);
                    virtualMachine.detach();
                    System.out.println(Log.cyan("Completed."));
                    System.out.println(Log.cyan("Logs will be printed in the target JVM."));
                    System.out.println(Log.cyan("Good luck !!!"));
                    System.out.println();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (!found) {
            System.out.println(Log.red("JVM not found. pid = " + args[0] + "."));
            System.out.println();
        }
    }
}