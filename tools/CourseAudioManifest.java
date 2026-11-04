import com.elderly.entity.CourseInfo;
import com.elderly.mapper.CourseInfoMapper;
import com.elderly.service.impl.TtsPrewarmService;
import com.elderly.config.ChatTtsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.Proxy;

/** Export exact server segmentation from a downloaded public /api/course/list response. */
class CourseAudioManifest {
    public static void main(String[] args) throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        var records = json.readTree(Files.readString(Path.of(args[0]))).path("data").path("records");
        List<CourseInfo> courses = new ArrayList<>();
        for (var row : records) courses.add(json.treeToValue(row, CourseInfo.class));
        CourseInfoMapper mapper = (CourseInfoMapper) Proxy.newProxyInstance(CourseInfoMapper.class.getClassLoader(),
                new Class[]{CourseInfoMapper.class}, (proxy, method, values) -> {
                    if (method.getName().equals("selectList")) return courses;
                    throw new UnsupportedOperationException(method.getName());
                });
        var service = new TtsPrewarmService(mapper, null, null, null, null, null, null, new ChatTtsConfig());
        try {
            var manifest = service.courseManifest();
            Files.writeString(Path.of(args[1]), json.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
            System.out.println("Courses=" + courses.size() + ", segments=" + manifest.size());
        } finally { service.shutdown(); }
    }
}
