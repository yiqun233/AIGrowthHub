package io.growthhub.security;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
 private final boolean enabled;private final io.growthhub.common.BusinessClock clock;public AuthController(Environment env,io.growthhub.common.BusinessClock clock){this.clock=clock;enabled=env.getProperty("AUTH_ENABLED",Boolean.class,false);}
 @GetMapping("/session") public ResponseEntity<Map<String,Object>> session(Authentication a,HttpServletRequest request){
  CsrfToken token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
  boolean authenticated=!enabled||a!=null&&a.isAuthenticated()&&!(a instanceof AnonymousAuthenticationToken);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("timezone",clock.zone().toString(),"enabled",enabled,"authenticated",authenticated,"username",a!=null&&authenticated?a.getName():"","csrfToken",token==null?"":token.getToken()));
 }
}
