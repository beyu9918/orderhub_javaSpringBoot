package orderhub.v1.common;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import orderhub.api.ApiException;
import orderhub.v1.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @Tag(name="V1 商品图片")
public class ImageController {
    private final Path directory;private final AuditService audit;
    public ImageController(@Value("${orderhub.v1.files-directory}") String directory,AuditService audit){this.directory=Path.of(directory).toAbsolutePath().normalize();this.audit=audit;}
    @PostMapping(value="/api/v1/admin/files/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,String> upload(@RequestPart("file") MultipartFile file) throws IOException {
        Db.check(!file.isEmpty()&&file.getSize()<=5*1024*1024,"图片不能为空且不能超过 5 MB");
        byte[] bytes=file.getBytes();
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(stream);Db.check(readers.hasNext(),"仅支持 PNG / JPEG 图片");var reader=readers.next();
            try{reader.setInput(stream);String format=reader.getFormatName();Db.check(Set.of("png","jpeg","jpg").contains(format.toLowerCase(Locale.ROOT)),"仅支持 PNG / JPEG 图片");
                Db.check(reader.getWidth(0)<=4096&&reader.getHeight(0)<=4096,"图片尺寸最多 4096×4096");
                BufferedImage image=reader.read(0);Db.check(image!=null,"无法读取图片");
                String id=UUID.randomUUID().toString();Files.createDirectories(directory);Path target=directory.resolve(id+".png");ImageIO.write(image,"png",target.toFile());
                audit.record(Actor.current(),"UPLOAD_IMAGE","FILE",null,null,Map.of("fileId",id));return Map.of("id",id,"url","/api/v1/images/"+id);
            }finally{reader.dispose();}
        }
    }
    @GetMapping("/api/v1/images/{id}") public ResponseEntity<Resource> image(@PathVariable String id){
        Path path=path(id);if(!Files.isRegularFile(path))throw ApiException.missing("图片不存在");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options","nosniff").body(new FileSystemResource(path));
    }
    @DeleteMapping("/api/v1/admin/files/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id) throws IOException{
        Files.deleteIfExists(path(id));audit.record(Actor.current(),"DELETE_IMAGE","FILE",null,Map.of("fileId",id),null);
    }
    private Path path(String id){
        if(!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw ApiException.missing("图片不存在");
        return directory.resolve(id+".png");
    }
}
