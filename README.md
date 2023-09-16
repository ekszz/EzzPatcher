# EzzPatcher

> A tiny runtime hot patch tool for JAVA

![GitHub](https://img.shields.io/github/license/ekszz/EzzPatcher)
![GitHub top language](https://img.shields.io/github/languages/top/ekszz/EzzPatcher)
![GitHub release (latest by SemVer including pre-releases)](https://img.shields.io/github/downloads-pre/ekszz/EzzPatcher/latest/total)


一款Java运行态字节码插桩工具。可以通过配置，在不重启应用的情况下，动态更新运行中的JVM里的字节码。

初衷是用于CTF比赛AWD模式下Java应用的快速修补，当然，它绝不局限于此。

- 基于JavaAgent
- 支持随应用启动和应用启动后attach两种模式
- 使用Yaml文件简便配置
- 支持不重启应用的情况下多次patch
- 支持已patch类的还原
- 支持配置文件不落地


## 0x01 构建
### Maven

使用Maven编译打包：

```
mvn clean package
```

将会生成 `target/EzzPatcher-1.x.x-jar-with-dependencies.jar`.

当然，你可以直接从releases下载打包好的jar包。

## 0x02 执行
### 通过Attach JVM来执行

如果目标JVM已经在运行中，可以通过attach的方式来执行。

每次执行时，会还原上次的修改，并根据最新的配置文件，重新对目标JVM中的类进行热补丁。

**JDK 8**
```
java -Xbootclasspath/a:<path-to-jdk>/lib/tools.jar -jar EzzPatcher-1.x.x-jar-with-dependencies.jar <java-pid>
```
or
```
java -Xbootclasspath/a:<path-to-jdk>/lib/tools.jar -jar EzzPatcher-1.x.x-jar-with-dependencies.jar <java-pid> <path-to-config>
```

**JDK 11 and newer**
```
java -jar EzzPatcher-1.x.x-jar-with-dependencies.jar <java-pid>
```
or
```
java -jar EzzPatcher-1.x.x-jar-with-dependencies.jar <java-pid> <path-to-config>
```

### 通过-javaagent参数随目标JVM启动

在目标JVM启动命令行中添加-javaagent参数来启动。
```
java -cp <class-path> -javaagent:EzzPatcher-1.x.x-jar-with-dependencies.jar <main-class> <arguments>
```
or
```
java -cp <class-path> -javaagent:EzzPatcher-1.x.x-jar-with-dependencies.jar=<path-to-config> <main-class> <arguments>
```

通过该方式启动应用后，依然可以使用上述attach JVM的方式来变更配置。

该方式主要用来变更一些应用启动时就用到的类。

## 0x03 配置文件

以下是一个示例配置文件。

> 如果在启动命令行中不带<path-to-config>参数的话，默认使用目标JVM工作目录下的config.yml文件。建议使用绝对路径指定。

```yaml
classPatchDefine: # 固定值
  # 需要修改的类名
  com.example.ezjava.controller.EvilController:
      # 需要修改的第1个方法名
      # 该类下所有需要修改的方法组成一个List
    - method: cmd
      # 该方法的参数列表（可选），List结构
      # 如果为空，应为paramType: []
      # 若无该参数，默认取同名函数中的某一个
      paramType:
        - java.lang.String
      # 可选，目前支持：
      # overwrite 覆盖（默认）
      # insertBefore 在原方法前插入代码
      # insertAfter 在原方法返回前插入代码
      # insertAt 在指定行数插入代码
      mode: overwrite
      # 你的代码
      code: '{ return "ok";}'

      # 需要修改的第2个方法名
    - method: cmd
      paramType: []
      mode: insertAt
      # 在该行数前插入代码
      # 当mode为insertAt时，此参数为必选
      insertAt: 23
      code: '{System.out.println("helloworld");return "yes!";}'
  
  # 需要修改的第2个类，这是一个最简单的配置的示例
  com.example.ezjava.beans.TargetClass:
    - method: getFlag
      code: 'return "ok";'
  
  com.example.ezjava.controller.DownloadController:
    - method: readFileCode
      paramType:
        - java.lang.String
      mode: insertBefore
      code: '{filePath="/tmp/nothing.txt";}'
```

> 注意：
> 
> 1. code字段中的代码应满足Javassist要求。
> 
> 2. Javassist中有一些特别的参数约定，如$0代表this，$1、$2...等代表第1、2...个参数，具体可参见Javassist文档。

如果你要还原所有变更，直接放置一个如下的配置文件后再次运行即可：

```yaml
classPatchDefine:

```

你可以将配置文件内容Base64编码后，放在<path-to-config>参数位置，以实现配置文件不落地。

例如，你要还原所有变更的话，在JDK11下，可以执行：

```
java -jar EzzPatcher-1.x.x-jar-with-dependencies.jar <java-pid> Y2xhc3NQYXRjaERlZmluZToNCg==
```

## 0x04 一些注意

Javaagent在被JVM加载之后，agent的相关类无法被卸载/更新，虽然一般没太大影响，但请谨慎使用。

仅在JDK8和JDK11上测试过。

## 0x05 免责声明

本项目仅面向软件研发调试、研究、学习，禁止任何非法用途。

如您在使用本项目的过程中存在任何非法行为，您需自行承担相应后果。

除非您已充分阅读、完全理解并接受本协议，否则，请您不要使用本项目。
