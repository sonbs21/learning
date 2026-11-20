import java.nio.file.Files;
import java.nio.file.Path;

public class ChuaXuLy {
    static String docFile() {
        return Files.readString(Path.of("menu.txt"));   // có thể ném IOException (checked)
    }
}
