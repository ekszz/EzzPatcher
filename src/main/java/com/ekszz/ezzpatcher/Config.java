package com.ekszz.ezzpatcher;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Config implements Serializable {

    final public static List<ClassMethodDefine> NULL_DEFINE = new ArrayList<>();

    /**
     * if value is NULL_DEFINE, then this class needs to be restored
     */
    private ConcurrentHashMap<String, List<ClassMethodDefine>> classPatchDefine = new ConcurrentHashMap<>();

    private ConcurrentHashMap<String, byte[]> backupCode = new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, List<ClassMethodDefine>> getClassPatchDefine() {
        return classPatchDefine;
    }

    public void setClassPatchDefine(ConcurrentHashMap<String, List<ClassMethodDefine>> classPatchDefine) {
        this.classPatchDefine = classPatchDefine;
    }

    public ConcurrentHashMap<String, byte[]> getBackupCode() {
        return backupCode;
    }

    /**
     * build classPatchDefine by config file
     *
     * @param path path to config
     * @return new classPatchDefine
     */
    @SuppressWarnings("unchecked")
    public ConcurrentHashMap<String, List<ClassMethodDefine>> loadFromLocalYAML(String path)
            throws FileNotFoundException {
        BufferedReader br = null;
        try {
            // try as a base64 string first
            try {
                br = new BufferedReader(new StringReader(
                        new String(Base64.getDecoder().decode(path), Charset.defaultCharset())));
            } catch (Exception e) {
                br = new BufferedReader(new FileReader(path));
            }

            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> yamlData = yaml.load(br);

            ConcurrentHashMap<String, List<ClassMethodDefine>> newClassPatchDefine = new ConcurrentHashMap<>();
            for (String key : this.getClassPatchDefine().keySet()) {
                if (this.getClassPatchDefine().get(key) != NULL_DEFINE) {
                    // classes need to restore
                    newClassPatchDefine.put(key, NULL_DEFINE);
                }
            }
            if (yamlData.containsKey("classPatchDefine")) {
                if (yamlData.get("classPatchDefine") instanceof Map) {
                    Map<String, ?> yamlClassPatchDefine = (Map<String, ?>) yamlData.get("classPatchDefine");
                    for (String perClass : yamlClassPatchDefine.keySet()) {
                        // process every class
                        if (!(yamlClassPatchDefine.get(perClass) instanceof List)) {
                            Log.warn("[-] Invalid config at classPatchDefine, it must be a LIST, skip.");
                            continue;
                        }
                        List yamlClassObjList = (List) yamlClassPatchDefine.get(perClass);
                        List<ClassMethodDefine> newPerClassMethodDefine = new ArrayList<>();
                        for (int i = 0; i < yamlClassObjList.size(); i++) {
                            // process every method
                            if (!(yamlClassObjList.get(i) instanceof Map)) {
                                Log.warn("[-] Invalid class define on {} at index {}, skip.", perClass, i);
                                continue;
                            }
                            Map<String, ?> yamlClassObj = (Map<String, ?>) yamlClassObjList.get(i);
                            if (!yamlClassObj.containsKey("method") || !yamlClassObj.containsKey("code")
                                    || !(yamlClassObj.get("method") instanceof String)
                                    || !(yamlClassObj.get("code") instanceof String)) {
                                Log.warn("[-] Invalid required parameter METHOD or CODE on {} at index {}, skip.",
                                        perClass, i);
                                continue;
                            }
                            if (yamlClassObj.containsKey("mode") &&
                                    (!(yamlClassObj.get("mode") instanceof String) ||
                                            (!"overwrite".equals(yamlClassObj.get("mode"))
                                                    && !"insertAt".equals(yamlClassObj.get("mode"))
                                                    && !"insertBefore".equals(yamlClassObj.get("mode"))
                                                    && !"insertAfter".equals(yamlClassObj.get("mode"))))) {
                                Log.warn("[-] parameter MODE on {} at index {} must be overwrite, insertAt," +
                                        " insertBefore or insertAfter, skip.", perClass, i);
                                continue;
                            }
                            Integer insertAt = null;
                            if ("insertAt".equals(yamlClassObj.get("mode"))) {
                                if (!yamlClassObj.containsKey("insertAt")) {
                                    Log.warn("[-] when MODE is insertAt, parameter INSERTAT is needed," +
                                            " on {} at index {}.", perClass, i);
                                    continue;
                                }
                                if (yamlClassObj.get("insertAt") instanceof Integer) {
                                    insertAt = (Integer) yamlClassObj.get("insertAt");
                                } else {
                                    Log.warn("[-] parameter INSERTAT must be a number, on {} at index {}.", perClass, i);
                                    continue;
                                }
                            }
                            if (yamlClassObj.containsKey("paramType")
                                    && !(yamlClassObj.get("paramType") instanceof List)) {
                                Log.warn("[-] parameter PARAMTYPE must be a LIST on {} at index {}, skip.",
                                        perClass, i);
                                continue;
                            }
                            ClassMethodDefine x = new ClassMethodDefine();
                            x.setMethod((String) yamlClassObj.get("method"));
                            x.setCode((String) yamlClassObj.get("code"));
                            if (yamlClassObj.containsKey("mode")) {
                                x.setMode((String) yamlClassObj.get("mode"));
                                if ("insertAt".equals(x.getMode())) {
                                    x.setInsertAt(insertAt);
                                }
                            } else {
                                x.setMode("overwrite");
                            }
                            if (yamlClassObj.containsKey("paramType")) {
                                List<String> paramType = new ArrayList<>();
                                List t = (List) yamlClassObj.get("paramType");
                                for (Object o : t) {
                                    paramType.add(o.toString());
                                }
                                x.setParamType(paramType);
                            }
                            newPerClassMethodDefine.add(x);
                        }
                        newClassPatchDefine.put(perClass.replace(".", "/"), newPerClassMethodDefine);
                    }
                }
            }
            return newClassPatchDefine;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
