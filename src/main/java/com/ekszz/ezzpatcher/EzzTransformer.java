package com.ekszz.ezzpatcher;

import javassist.*;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EzzTransformer implements ClassFileTransformer {
    private Config config;

    public EzzTransformer(Config config) {
        this.config = config;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        String dotClassName = className.replace("/", ".");
        if (!"none".equals(config.getClassDumpDefine().get(Config.CONF_KEY_DUMP_FILTER_TYPE))) {
            if ("false".equals(config.getClassDumpDefine().get(Config.CONF_KEY_DUMP_SKIP_JDK))
                    || !dotClassName.startsWith("sun.") && !dotClassName.startsWith("java.")
                    && !dotClassName.startsWith("javax.") && !dotClassName.startsWith("jdk.")
                    && !dotClassName.startsWith("com.sun.")) {
                boolean needToDump = false;
                String filterValue = config.getClassDumpDefine().get(Config.CONF_KEY_DUMP_FILTER_VALUE);
                if ("regex".equals(config.getClassDumpDefine().get(Config.CONF_KEY_DUMP_FILTER_TYPE))) {
                    Pattern pattern = Pattern.compile(filterValue);
                    Matcher m = pattern.matcher(dotClassName);
                    if (m.matches()) {
                        needToDump = true;
                    }
                } else if ("prefix".equals(config.getClassDumpDefine().get(Config.CONF_KEY_DUMP_FILTER_TYPE))) {
                    if (dotClassName.startsWith(filterValue)) {
                        needToDump = true;
                    }
                }
                if (needToDump) {
                    Log.debug("[*] Trying to dump class: {}", dotClassName);
                    String fullPath = config.getClassDumpDefine().get(Config.CONF_KEY_DUMP_SAVE_PATH);
                    Path filePath = Paths.get(fullPath + (fullPath.endsWith(File.separator) ? "" : File.separator) + className + ".class");
                    if (Files.exists(filePath)) {
                        Log.info("[-] Class dump file already exist: {}, skip.", className + ".class");
                    } else {
                        byte[] classBytes = config.getBackupCode().getOrDefault(className, classfileBuffer);
                        try {
                            Files.createDirectories(filePath.getParent());
                            Files.write(filePath, classBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                            Log.info("[+] Dump class success: {}", dotClassName);
                        } catch (Throwable e) {
                            Log.warn("[-] Dump class fail: {}.", dotClassName);
                            Log.warn(e);
                        }
                    }
                }
            }
        }

        if (config.getClassPatchDefine().containsKey(className)) {
            List<ClassMethodDefine> classMethodDefineList = config.getClassPatchDefine().get(className);
            if (classMethodDefineList == Config.NULL_DEFINE) {
                // restore class
                if (config.getBackupCode().containsKey(className)) {
                    Log.info("[+] Restored class: {}", dotClassName);
                    config.getClassPatchDefine().remove(className);
                    return config.getBackupCode().get(className);
                } else {
                    Log.error("[!] Restore class failed, no backup: {}", dotClassName);
                }
            } else {
                // transform class
                Log.debug("[*] Hooking class: {}", dotClassName);
                if (!config.getBackupCode().containsKey(className)) {
                    config.getBackupCode().put(className, Arrays.copyOf(classfileBuffer, classfileBuffer.length));
                }
                try {
                    if (classMethodDefineList.size() == 1 && "_".equals(classMethodDefineList.get(0).getMethod())) {
                        // overwrite class
                        byte[] bytes = Base64.getDecoder().decode(classMethodDefineList.get(0).getCode());
                        Log.info("[+] Hook Ready: {}, {} bytes.", dotClassName, bytes.length);
                        return bytes;
                    } else {
                        // modify every method
                        ClassPool classPool = new ClassPool(ClassPool.getDefault());
                        classPool.insertClassPath(new ByteArrayClassPath(dotClassName, config.getBackupCode().get(className)));
                        ClassClassPath classPath = new ClassClassPath(this.getClass());
                        classPool.appendClassPath(classPath);
                        classPool.appendClassPath(new LoaderClassPath(loader));

                        CtClass clazz = classPool.get(dotClassName);
                        List<String> res = new ArrayList<>();
                        for (ClassMethodDefine classMethodDefine : classMethodDefineList) {
                            CtMethod targetMethod;
                            if (classMethodDefine.getParamType() == null) {
                                targetMethod = clazz.getDeclaredMethod(classMethodDefine.getMethod());
                            } else {
                                CtClass[] paramType = new CtClass[classMethodDefine.getParamType().size()];
                                for (int i = 0; i < classMethodDefine.getParamType().size(); i++) {
                                    paramType[i] = classPool.get(classMethodDefine.getParamType().get(i));
                                }
                                targetMethod = clazz.getDeclaredMethod(classMethodDefine.getMethod(), paramType);
                            }
                            // modify method code
                            if ("insertBefore".equals(classMethodDefine.getMode())) {
                                targetMethod.insertBefore(classMethodDefine.getCode());
                            } else if ("insertAfter".equals(classMethodDefine.getMode())) {
                                targetMethod.insertAfter(classMethodDefine.getCode());
                            } else if ("insertAt".equals(classMethodDefine.getMode())) {
                                targetMethod.insertAt(classMethodDefine.getInsertAt(), classMethodDefine.getCode());
                            } else {
                                targetMethod.setBody(classMethodDefine.getCode());
                            }
                            res.add(classMethodDefine.getMethod());
                        }
                        byte[] byteCode = clazz.toBytecode();
                        clazz.detach();
                        Log.info("[+] Hook ready: {} on methods {}.", dotClassName, res);
                        return byteCode;
                    }
                } catch (Throwable e) {
                    Log.warn("[-] Hook fail: {}", dotClassName);
                    Log.warn(e);
                }
            }
        }
        return null;
    }
}
