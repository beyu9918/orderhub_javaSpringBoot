package orderhub.v1.audit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import orderhub.v1.common.*;
import orderhub.v1.audit.entity.AuditLog;
import orderhub.v1.audit.mapper.AuditLogMapper;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/admin/audit-logs") @Tag(name="V1 管理审计")
public class AuditController {
    private final AuditLogMapper logs;public AuditController(AuditLogMapper logs){this.logs=logs;}
    @GetMapping public PageResult<AuditLog> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) Long adminId,@RequestParam(required=false) String action){
        var q=new QueryWrapper<AuditLog>();if(adminId!=null)q.eq("admin_id",adminId);if(action!=null)q.eq("action",action);return PageResult.of(logs,page,size,q.orderByDesc("created_at","id"));
    }
    @GetMapping("/{id}") public AuditLog detail(@PathVariable long id){return Db.require(logs.selectById(id));}
}
