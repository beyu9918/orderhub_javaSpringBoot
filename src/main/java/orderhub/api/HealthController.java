package orderhub.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
    @GetMapping("/api/health") @SecurityRequirements
    public Map<String, String> health() { return Map.of("status", "UP"); }
}
