package com.ekszz.ezzpatcher;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Config implements Serializable {

    final public static String CONF_KEY_KEEP_CONFIG = "keepConfig";
    final public static String CONF_KEY_NO_LOGO = "noLogo";
    final public static String CONF_KEY_LOG_LEVEL = "logLevel";
    final public static List<String> ALLOW_LOG_LEVEL = Arrays.asList("DEBUG", "INFO", "WARNING", "ERROR", "NONE");

    final public static List<ClassMethodDefine> NULL_DEFINE = new ArrayList<>();

    public ConcurrentHashMap<String, String> config = new ConcurrentHashMap<String, String>() {{
        put(CONF_KEY_KEEP_CONFIG, "false");
        put(CONF_KEY_NO_LOGO, "false");
        put(CONF_KEY_LOG_LEVEL, "INFO");
    }};

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

    public ConcurrentHashMap<String, String> getConfig() {
        return config;
    }

    public void setConfig(ConcurrentHashMap<String, String> config) {
        this.config = config;
    }

    /**
     * parse YAML from local file or base64 str
     *
     * @param path local file or base64 str
     * @return parameter map
     */
    public Map<String, Object> readFromLocalYAML(String path) throws FileNotFoundException {
        if ("restore".equalsIgnoreCase(path)) {
            path = "Y2xhc3NQYXRjaERlZmluZToNCg==";
        }
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
            return yaml.load(br);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * get ClassMethodDefine result from yaml map
     *
     * @param yamlData yaml map
     * @return new define
     */
    public ConcurrentHashMap<String, List<ClassMethodDefine>> getClassMethodDefine(Map<String, Object> yamlData) {
        // process classPatchDefine segment
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
                    if (yamlClassPatchDefine.get(perClass) instanceof List) {
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
                    } else if (yamlClassPatchDefine.get(perClass) instanceof String) {
                        // base64
                        String yamlClassObjString = (String) yamlClassPatchDefine.get(perClass);
                        try {
                            Base64.getDecoder().decode(yamlClassObjString);
                            List<ClassMethodDefine> newPerClassMethodDefine = new ArrayList<>();
                            ClassMethodDefine x = new ClassMethodDefine();
                            x.setMethod("_");
                            x.setCode(yamlClassObjString);
                            newPerClassMethodDefine.add(x);
                            newClassPatchDefine.put(perClass.replace(".", "/"), newPerClassMethodDefine);
                        } catch (IllegalArgumentException e) {
                            Log.warn("[-] Invalid base64 of class bytes define on {}, skip.", perClass);
                        }
                    } else {
                        Log.warn("[-] Invalid config at classPatchDefine, it must be a LIST or BASE64, skip.");
                    }
                }
            }
        }
        return newClassPatchDefine;
    }

    /**
     * reload from yaml map, real time. It must not output anything.
     *
     * @param yamlData yaml map
     * @return reload or keep
     */
    @SuppressWarnings("unchecked")
    public boolean reloadConfig(Map<String, Object> yamlData) {
        // process config segment
        boolean isReload = false;
        if (yamlData.containsKey("config")) {
            if (yamlData.get("config") instanceof Map) {
                Map<String, ?> configDefine = (Map<String, ?>) yamlData.get("config");
                // judge keep or update config
                if (config.containsKey(CONF_KEY_KEEP_CONFIG)
                        || configDefine.containsKey(CONF_KEY_KEEP_CONFIG)
                        && configDefine.get(CONF_KEY_KEEP_CONFIG) instanceof Boolean
                        && !(Boolean) configDefine.get(CONF_KEY_KEEP_CONFIG)) {
                    isReload = true;
                    for (String configKey : configDefine.keySet()) {
                        if (CONF_KEY_NO_LOGO.equals(configKey)
                                && configDefine.get(CONF_KEY_NO_LOGO) instanceof Boolean) {
                            if (Boolean.TRUE.equals(configDefine.get(CONF_KEY_NO_LOGO))) {
                                config.put(CONF_KEY_NO_LOGO, "true");
                            } else {
                                config.put(CONF_KEY_NO_LOGO, "false");
                            }
                        }
                        if (CONF_KEY_LOG_LEVEL.equals(configKey)
                                && configDefine.get(CONF_KEY_LOG_LEVEL) instanceof String) {
                            if (ALLOW_LOG_LEVEL.contains((String) configDefine.get(CONF_KEY_LOG_LEVEL))) {
                                config.put(CONF_KEY_LOG_LEVEL, (String) configDefine.get(CONF_KEY_LOG_LEVEL));
                            }
                        }
                    }
                }
            }
        }
        config.remove(CONF_KEY_KEEP_CONFIG);
        return isReload;
    }
}
