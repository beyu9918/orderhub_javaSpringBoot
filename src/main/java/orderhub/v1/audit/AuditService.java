package orderhub.v1.audit;

import orderhub.v1.audit.entity.AuditLog;
import orderhub.v1.audit.mapper.AuditLogMapper;
import orderhub.v1.common.*;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.*;

@Service
public class AuditService {
    private final AuditLogMapper logs;
    public AuditService(AuditLogMapper logs) { this.logs=logs; }
    public void record(Actor actor,String action,String type,Long id,Object before,Object after) {
        if (!actor.admin()) return;
        AuditLog log=new AuditLog(); log.adminId=actor.id(); log.action=action; log.resourceType=type; log.resourceId=id;
        log.beforeValue=before==null?null:JsonbHandler.object(before); log.afterValue=after==null?null:JsonbHandler.object(after);
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request)
            log.ipAddress=request.getRequest().getRemoteAddr();
        logs.insert(log);
    }
}
