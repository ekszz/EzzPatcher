package com.ekszz.ezzpatcher;

import javassist.*;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EzzTransformer implements ClassFileTransformer {
    private Config config;

    public EzzTransformer(Config config) {
        this.config = config;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (config.getClassPatchDefine().containsKey(className)) {
            String dotClassName = className.replace("/", ".");
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
                    Log.info("[+] Hook success: {} on methods {}.", dotClassName, res);
                    return byteCode;
                } catch (Throwable e) {
                    Log.warn("[-] Hook fail: {}", dotClassName);
                    Log.warn(e);
                }
            }
        }
        return null;
    }
}
