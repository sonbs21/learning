# 18.17.9. Build file nén thực thi từ Ant mà không dùng spring-boot-antlib (Build an Executable Archive From Ant without Using spring-boot-antlib)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.build.build-an-executable-archive-with-ant-without-using-spring-boot-antlib>

Để build với Ant, bạn cần lấy các dependency, biên dịch, và sau đó tạo một file nén jar hoặc war. Để làm cho nó thực thi được, bạn có thể dùng module `spring-boot-antlib` hoặc làm theo các hướng dẫn sau:

1. Nếu bạn đang build một jar, hãy đóng gói các class và resource của ứng dụng trong một thư mục lồng nhau `BOOT-INF/classes`. Nếu bạn đang build một war, hãy đóng gói các class của ứng dụng trong một thư mục lồng nhau `WEB-INF/classes` như thường lệ.
2. Thêm các dependency runtime vào một thư mục lồng nhau `BOOT-INF/lib` đối với jar hoặc `WEB-INF/lib` đối với war. Hãy nhớ **không** nén các mục trong file nén.
3. Thêm các dependency `provided` (embedded container) vào một thư mục lồng nhau `BOOT-INF/lib` đối với jar hoặc `WEB-INF/lib-provided` đối với war. Hãy nhớ **không** nén các mục trong file nén.
4. Thêm các class `spring-boot-loader` vào gốc của file nén (để `Main-Class` có sẵn).
5. Dùng launcher phù hợp (chẳng hạn như `JarLauncher` cho file jar) làm thuộc tính `Main-Class` trong manifest và chỉ định các property khác mà nó cần dưới dạng các mục manifest — chủ yếu bằng cách đặt property `Start-Class`.

Ví dụ sau cho thấy cách build một file nén thực thi với Ant:

```xml
<target name="build" depends="compile">
    <jar destfile="target/${ant.project.name}-${spring-boot.version}.jar" compress="false">
        <mappedresources>
            <fileset dir="target/classes" />
            <globmapper from="*" to="BOOT-INF/classes/*"/>
        </mappedresources>
        <mappedresources>
            <fileset dir="src/main/resources" erroronmissingdir="false"/>
            <globmapper from="*" to="BOOT-INF/classes/*"/>
        </mappedresources>
        <mappedresources>
            <fileset dir="${lib.dir}/runtime" />
            <globmapper from="*" to="BOOT-INF/lib/*"/>
        </mappedresources>
        <zipfileset src="${lib.dir}/loader/spring-boot-loader-jar-${spring-boot.version}.jar" />
        <manifest>
            <attribute name="Main-Class" value="org.springframework.boot.loader.launch.JarLauncher" />
            <attribute name="Start-Class" value="${start-class}" />
        </manifest>
    </jar>
</target>
```
