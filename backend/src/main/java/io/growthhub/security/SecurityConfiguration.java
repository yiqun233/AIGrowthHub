package io.growthhub.security;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
@Configuration
public class SecurityConfiguration {
 private final boolean enabled;private final Environment env;
 public SecurityConfiguration(Environment env){this.env=env;enabled=env.getProperty("AUTH_ENABLED",Boolean.class,false);
  String address=env.getProperty("server.address","127.0.0.1");
  if(!enabled&&!Set.of("127.0.0.1","localhost","::1").contains(address))throw new IllegalStateException("非回环监听必须开启 AUTH_ENABLED");
 }
 @Bean public UserDetailsService users(){
  if(!enabled)return new InMemoryUserDetailsManager();String password=env.getProperty("AUTH_PASSWORD",""),hash=env.getProperty("AUTH_PASSWORD_HASH","");
  if(hash.isBlank()){if(password.length()<12||password.startsWith("replace-with-"))throw new IllegalStateException("启用登录需配置至少 12 字符的 AUTH_PASSWORD 或 BCrypt AUTH_PASSWORD_HASH");hash=new BCryptPasswordEncoder(12).encode(password);}
  if(!hash.matches("\\$2[aby]\\$\\d{2}\\$.{53}"))throw new IllegalStateException("AUTH_PASSWORD_HASH 必须为 BCrypt 哈希");
  return new InMemoryUserDetailsManager(User.withUsername(env.getProperty("AUTH_USERNAME","growthhub")).password("{bcrypt}"+hash).roles("OWNER").build());
 }
 private static void error(HttpServletResponse response,int status,String code,String message)throws IOException{response.setStatus(status);response.setContentType("application/json;charset=UTF-8");response.setHeader("Cache-Control","no-store");response.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\",\"fieldErrors\":{},\"requestId\":\""+UUID.randomUUID()+"\"}");}
 @Bean public SecurityFilterChain chain(HttpSecurity http)throws Exception{
  if(!enabled){http.csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().permitAll());return http.build();}
  http.authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/session","/api/v1/auth/login","/actuator/health","/error").permitAll().anyRequest().authenticated())
   .requestCache(c->c.disable())
   .exceptionHandling(e->e.authenticationEntryPoint((q,s,x)->error(s,401,"UNAUTHENTICATED","请先登录或重新登录"))
    .accessDeniedHandler((q,s,x)->error(s,403,"CSRF_OR_ACCESS_DENIED","会话校验失败，请刷新页面后重试")))
   .formLogin(f->f.loginProcessingUrl("/api/v1/auth/login").successHandler((q,s,a)->{s.setContentType("application/json");s.getWriter().write("{\"authenticated\":true}");}).failureHandler((q,s,x)->error(s,401,"INVALID_CREDENTIALS","用户名或密码不正确")))
   .logout(l->l.logoutUrl("/api/v1/auth/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("GROWTHHUB_SESSION").logoutSuccessHandler((q,s,a)->{s.setContentType("application/json");s.getWriter().write("{\"authenticated\":false}");}))
   .addFilterBefore(new LoginThrottle(),UsernamePasswordAuthenticationFilter.class);
  return http.build();
 }
 static class LoginThrottle extends OncePerRequestFilter {
  private long window=System.currentTimeMillis();private int attempts;
  private synchronized boolean allowed(){long now=System.currentTimeMillis();if(now-window>60000){window=now;attempts=0;}return ++attempts<=15;}
  @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse s,FilterChain chain)throws ServletException,IOException{
   if(q.getMethod().equals("POST")&&q.getServletPath().equals("/api/v1/auth/login")&&!allowed()){s.setHeader("Retry-After","60");error(s,429,"LOGIN_RATE_LIMIT","登录尝试过于频繁，请稍后重试");return;}chain.doFilter(q,s);
  }
 }
}
