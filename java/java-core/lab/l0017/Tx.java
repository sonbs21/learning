import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Annotation tự viết, bắt chước @Transactional của Spring
@Retention(RetentionPolicy.RUNTIME)   // giữ lại lúc chạy để reflection đọc được
@Target(ElementType.METHOD)
public @interface Tx { }
